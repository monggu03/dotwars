package com.dongguk.dotwars.auth.service;

import com.dongguk.dotwars.auth.jwt.JwtTokenProvider;
import com.dongguk.dotwars.auth.kakao.KakaoOAuthClient;
import com.dongguk.dotwars.auth.kakao.KakaoTokenResponse;
import com.dongguk.dotwars.auth.kakao.KakaoUserInfoResponse;
import com.dongguk.dotwars.user.domain.User;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 카카오 로그인 흐름의 오케스트레이션 + 인증 쿠키 빌더.
 *
 * 컨트롤러를 얇게 유지하기 위해 다음을 모두 여기서 처리:
 *  - 카카오 토큰 교환
 *  - 카카오 사용자 정보 조회
 *  - 신규/기존 사용자 식별 (findByKakaoId orElseGet save)
 *  - JWT 발급
 *  - 단과대 선택 여부에 따른 리다이렉트 경로 결정
 *  - HttpOnly + SameSite 쿠키 생성 (생성/만료 동일 빌더)
 */
@Service
@Transactional
@Slf4j
public class AuthService {

    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final long accessTokenValiditySeconds;
    private final boolean cookieSecure;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${auth.cookie.secure}") boolean cookieSecure
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.cookieSecure = cookieSecure;
    }

    /**
     * 카카오 콜백 처리의 핵심 메서드.
     * @return 쿠키에 박을 JWT 와 클라이언트가 이동할 페이지 경로.
     */
    public LoginResult handleKakaoCallback(String code) {
        KakaoTokenResponse tokens = kakaoOAuthClient.getAccessToken(code);
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(tokens.accessToken());

        User user = userRepository.findByKakaoId(userInfo.id())
                .orElseGet(() -> registerNewUser(userInfo.id()));

        String jwt = jwtTokenProvider.createToken(user.getId());
        // 단과대 미선택 = 첫 가입 또는 미완료 → 단과대 선택 페이지로.
        // user.getDepartment() 는 LAZY 라 트랜잭션 안에서 접근해야 함 → @Transactional 필수.
        String target = (user.getDepartment() == null) ? "/select-department.html" : "/game.html";

        log.info("[auth] 로그인 완료 user.id={} new={} target={}",
                user.getId(), user.getDepartment() == null, target);
        return new LoginResult(jwt, target);
    }

    /**
     * 신규 사용자 등록. nickname 은 닉네임 미수집 정책에 따라 카카오 ID 기반으로 자동 생성.
     * 화면 표시용으로는 "user_12345678" 식으로 노출됨 (충돌 가능성 0).
     */
    private User registerNewUser(Long kakaoId) {
        String autoNickname = "user_" + kakaoId;
        User saved = userRepository.save(User.from(kakaoId, autoNickname));
        log.info("[auth] 신규 가입 user.id={} kakao.id={}", saved.getId(), kakaoId);
        return saved;
    }

    /** 로그인 직후 응답에 박을 액세스 토큰 쿠키. */
    public ResponseCookie buildAccessTokenCookie(String jwt) {
        return baseCookieBuilder(ACCESS_TOKEN_COOKIE, jwt)
                .maxAge(Duration.ofSeconds(accessTokenValiditySeconds))
                .build();
    }

    /** 로그아웃 시 같은 이름의 쿠키를 max-age=0 으로 덮어써 브라우저가 즉시 삭제하도록. */
    public ResponseCookie buildLogoutCookie() {
        return baseCookieBuilder(ACCESS_TOKEN_COOKIE, "")
                .maxAge(0)
                .build();
    }

    /**
     * 쿠키 공통 속성:
     *  - HttpOnly: JavaScript 에서 document.cookie 로 못 읽음 → XSS 로 토큰 탈취 방어
     *  - Secure: HTTPS 전송 시에만 (로컬 개발용 yml 에서 false)
     *  - SameSite=Lax: top-level 네비게이션(링크 클릭)엔 보내지만 cross-site POST 엔 안 보냄 → CSRF 일부 방어
     *    (Strict 는 카카오 콜백 redirect 시 쿠키가 같이 못 와서 안 됨. Lax 가 적절)
     *  - Path=/: 도메인 전체에서 사용
     */
    private ResponseCookie.ResponseCookieBuilder baseCookieBuilder(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/");
    }

    /** 콜백 처리 결과 — 응답에 박을 JWT + 리다이렉트할 페이지. */
    public record LoginResult(String jwt, String targetPath) {}
}
