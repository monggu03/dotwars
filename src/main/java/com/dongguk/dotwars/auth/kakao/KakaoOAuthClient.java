package com.dongguk.dotwars.auth.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 OAuth 2.0 클라이언트.
 *
 * 두 가지 단계만 책임:
 *   1) 인증 코드 → 액세스 토큰 (POST /oauth/token)
 *   2) 액세스 토큰 → 사용자 정보 (GET /v2/user/me)
 *
 * HTTP 클라이언트로 RestClient 선택 이유:
 *  - RestTemplate: Spring 6 에서 유지보수 모드. 새 코드에 도입 비추.
 *  - WebClient: 리액티브 스타일. 우리는 동기 흐름이라 오버스펙.
 *  - RestClient: Spring 6.1+ 의 새 표준. 동기 + Fluent API + Builder 기반 설정 → 정답.
 *
 * 같은 Builder 에서 두 개의 RestClient 인스턴스를 만들어 두 base URL 을 분리.
 * (kauth.kakao.com: 인증/토큰, kapi.kakao.com: 사용자 정보. 카카오가 도메인을 두 개로 분리해둠)
 */
@Component
@Slf4j
public class KakaoOAuthClient {

    private final RestClient kauthClient;       // https://kauth.kakao.com (토큰 발급)
    private final RestClient kapiClient;        // https://kapi.kakao.com  (사용자 정보)
    private final KakaoProperties props;

    public KakaoOAuthClient(RestClient.Builder builder, KakaoProperties props) {
        // tokenUri 에서 호스트만 잘라 base URL 로 사용. 운영에서 도메인이 바뀔 가능성 대비 properties 의존.
        this.kauthClient = builder.clone().baseUrl(extractBaseUrl(props.tokenUri())).build();
        this.kapiClient  = builder.clone().baseUrl(extractBaseUrl(props.userInfoUri())).build();
        this.props = props;
    }

    /**
     * 인증 코드를 카카오 토큰으로 교환.
     *
     * 카카오는 POST 바디를 application/x-www-form-urlencoded 로 받음 (OAuth 2.0 spec 그대로).
     * MultiValueMap 을 바디로 넘기면 RestClient 가 자동으로 form encoding 처리.
     */
    public KakaoTokenResponse getAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("code", code);

        KakaoTokenResponse response = kauthClient.post()
                .uri(extractPath(props.tokenUri()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);

        // access_token 자체는 로그에 남기지 않음 (XSS/로그 유출 시 그대로 탈취당함).
        log.debug("[kakao] access token 발급 성공 expires_in={}",
                response == null ? null : response.expiresIn());
        return response;
    }

    /**
     * 액세스 토큰으로 카카오 사용자 정보 조회.
     * Authorization 헤더는 OAuth 2.0 Bearer 스키마.
     */
    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        KakaoUserInfoResponse response = kapiClient.get()
                .uri(extractPath(props.userInfoUri()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserInfoResponse.class);

        log.debug("[kakao] user info 조회 성공 kakaoId={}",
                response == null ? null : response.id());
        return response;
    }

    // "https://kauth.kakao.com/oauth/token" → "https://kauth.kakao.com"
    private String extractBaseUrl(String fullUri) {
        int pathStart = fullUri.indexOf('/', "https://".length());
        return pathStart < 0 ? fullUri : fullUri.substring(0, pathStart);
    }

    // "https://kauth.kakao.com/oauth/token" → "/oauth/token"
    private String extractPath(String fullUri) {
        int pathStart = fullUri.indexOf('/', "https://".length());
        return pathStart < 0 ? "/" : fullUri.substring(pathStart);
    }
}
