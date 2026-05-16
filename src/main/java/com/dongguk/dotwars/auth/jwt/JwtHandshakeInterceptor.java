package com.dongguk.dotwars.auth.jwt;

import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 핸드셰이크 인증 — HTTP 단계에서 쿠키로 JWT 검증.
 *
 * 왜 HandshakeInterceptor 인가 (STOMP CONNECT 헤더 대신):
 *  - SockJS 의 핸드셰이크는 HTTP 요청 → 브라우저가 accessToken 쿠키를 자동 동봉.
 *  - 우리 쿠키는 HttpOnly 라 JS 가 못 읽음 (XSS 방어).
 *  - 그러므로 클라이언트에서 토큰을 STOMP CONNECT 헤더로 손수 넣을 수 없음.
 *  - 반면 HTTP 단계에선 쿠키가 자연스럽게 도착하므로 여기서 검증하는 게 가장 안전 + 단순.
 *
 * 동작:
 *  1. WebSocket 연결 요청의 쿠키에서 accessToken 추출
 *  2. JwtTokenProvider 로 검증
 *  3. 검증 통과 시 userId 를 attributes 에 저장 → 이후 JwtHandshakeHandler 가 Principal 로 변환
 *  4. 검증 실패 시 false 반환 → 핸드셰이크 자체 실패 (HTTP 401)
 *
 * 매 메시지 검증 X — 한 번 통과한 연결은 그 위에 흐르는 모든 메시지를 인증된 것으로 간주.
 * 토큰 만료 후에도 끊기 전까진 메시지가 흐를 수 있는 점은 학습 단계에선 수용.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractTokenFromCookies(request);
        if (token == null) {
            log.debug("[ws-handshake] accessToken 쿠키 없음 — 핸드셰이크 거절");
            return false;
        }
        if (!jwtTokenProvider.validateToken(token)) {
            log.debug("[ws-handshake] JWT 검증 실패 — 핸드셰이크 거절");
            return false;
        }
        Long userId = jwtTokenProvider.getUserId(token);
        // 후속 단계(JwtHandshakeHandler)에서 Principal 로 변환할 수 있도록 attributes 에 저장.
        attributes.put(ATTR_USER_ID, userId);
        log.debug("[ws-handshake] 인증 성공 userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 후처리 불필요. 인증은 beforeHandshake 에서 완료.
    }

    /**
     * ServerHttpRequest → 쿠키 배열 → accessToken 추출.
     * ServletServerHttpRequest 래핑 시 표준 javax/jakarta Cookie 접근 가능.
     */
    private String extractTokenFromCookies(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
