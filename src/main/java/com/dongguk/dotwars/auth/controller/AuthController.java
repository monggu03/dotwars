package com.dongguk.dotwars.auth.controller;

import com.dongguk.dotwars.auth.kakao.KakaoProperties;
import com.dongguk.dotwars.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 인증 흐름의 진입점.
 *
 * 3개 엔드포인트:
 *  - GET  /api/auth/kakao/login    → 카카오 OAuth 페이지로 302 리다이렉트
 *  - GET  /api/auth/kakao/callback → 토큰 교환 + 사용자 처리 + JWT 쿠키 + 페이지 리다이렉트
 *  - POST /api/auth/logout         → 쿠키 만료 + 200 OK
 *
 * 컨트롤러는 HTTP 만 책임 (파라미터 수령, 쿠키/리다이렉트 박기). 비즈니스 로직은 AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final KakaoProperties kakaoProperties;
    private final AuthService authService;

    /**
     * 카카오 로그인 시작 — 사용자를 카카오 OAuth 동의 화면으로 보냄.
     *
     * 구성 URL 예:
     *   https://kauth.kakao.com/oauth/authorize
     *     ?response_type=code
     *     &client_id=...
     *     &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fauth%2Fkakao%2Fcallback
     *
     * redirect_uri 는 URL-encode 필수 — 콜론/슬래시 가 쿼리스트링 내에서 그대로면
     * 카카오가 잘못 파싱할 수 있음.
     */
    @GetMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response) throws IOException {
        String encodedRedirect = URLEncoder.encode(
                kakaoProperties.redirectUri(),
                StandardCharsets.UTF_8
        );
        String authorizationUrl = "%s?response_type=code&client_id=%s&redirect_uri=%s".formatted(
                kakaoProperties.authorizationUri(),
                kakaoProperties.clientId(),
                encodedRedirect
        );
        log.debug("[auth] 카카오 인증 페이지로 리다이렉트");
        response.sendRedirect(authorizationUrl);
    }

    /**
     * 카카오 콜백 — 카카오가 사용자 동의 후 우리 서버로 보내는 진입점.
     *
     * 흐름:
     *  1) AuthService 가 카카오와 통신 + 사용자 식별/생성 + JWT 발급
     *  2) JWT 를 HttpOnly 쿠키로 박음 → 이후 모든 요청에 자동 동봉
     *  3) 단과대 선택 여부에 따라 적절한 페이지로 리다이렉트
     *
     * 사용자가 카카오에서 취소(거부) 한 경우 카카오는 code 대신 error 파라미터로 회신.
     * 그 경우 RequestParam 매핑이 실패하므로 별도 에러 핸들러에서 잡아 안내 페이지로 보내야 함 (추후 단계).
     */
    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam("code") String code,
                              HttpServletResponse response) throws IOException {
        AuthService.LoginResult result = authService.handleKakaoCallback(code);

        ResponseCookie cookie = authService.buildAccessTokenCookie(result.jwt());
        // Servlet API 의 Cookie 클래스는 SameSite 미지원이라 Set-Cookie 헤더로 직접 추가.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.sendRedirect(result.targetPath());
    }

    /**
     * 로그아웃 — 쿠키만 비우면 클라이언트는 다시 미인증 상태.
     * 서버는 stateless 라 별도 invalidation 처리할 게 없음 (JWT 만료까지는 그 토큰 자체는 여전히 유효 —
     * 진짜로 즉시 무효화하려면 토큰 블랙리스트 도입 필요. 학습 단계엔 과한 복잡도라 미도입).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = authService.buildLogoutCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
