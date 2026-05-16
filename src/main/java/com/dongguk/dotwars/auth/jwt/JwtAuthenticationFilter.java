package com.dongguk.dotwars.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 요청마다 한 번씩 실행되어 JWT 를 검증하고 인증 정보를 SecurityContext 에 채움.
 *
 * 토큰 소스 우선순위:
 *   1) Authorization 헤더 (Bearer 스키마) — Postman/모바일 클라이언트에서 주로 사용
 *   2) accessToken 쿠키            — 브라우저에서 HttpOnly 쿠키로 들고 다닐 때
 * 둘 중 먼저 발견된 토큰만 사용. 토큰이 없거나 검증 실패면 SecurityContext 를 채우지 않고
 * 다음 필터로 흘려보냄 → Spring Security 가 미인증 사용자로 처리.
 *
 * 왜 @Component 안 붙였나:
 *   Spring Boot 가 @Component 필터를 일반 서블릿 필터로 자동 등록함.
 *   동시에 SecurityFilterChain 에도 추가되면 같은 요청에 필터가 2번 실행되는 함정 발생.
 *   → SecurityConfig 에서만 new 로 인스턴스화해 등록. 일반 서블릿 체인엔 등록되지 않음.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            // principal 에 userId(Long) 그대로 저장 → 컨트롤러에서 @AuthenticationPrincipal Long 으로 받기.
            // credentials/authorities 는 비어있음. 권한 모델은 도입 시점에 채울 예정.
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 헤더 → 쿠키 순으로 토큰 탐색.
     * 헤더 우선: API 클라이언트의 표준 방식. 헤더에 토큰이 있으면 쿠키는 쳐다보지 않음.
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
