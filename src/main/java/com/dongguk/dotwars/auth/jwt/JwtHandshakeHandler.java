package com.dongguk.dotwars.auth.jwt;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 의 Principal 결정자.
 *
 * Spring 의 DefaultHandshakeHandler 가 기본적으로 HttpServletRequest 의 Principal 을 사용하는데,
 * 우리는 SecurityContext 대신 JwtHandshakeInterceptor 가 attributes 에 박은 userId 를 쓰고 싶음.
 * → determineUser() 오버라이드해 attributes 의 userId 를 Principal 로 변환.
 *
 * 효과:
 *  - 이후 컨트롤러 (@MessageMapping 등) 에서 Principal 로 사용자 식별 가능
 *  - SimpMessagingTemplate.convertAndSendToUser(name, ...) 로 특정 사용자에게만 알림 가능
 *  - 현재 단계는 broadcast 만 쓰지만, 추후 개인 알림(쿨다운 끝 등) 도입 시 즉시 활용
 */
@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get(JwtHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            // 정상 흐름이면 interceptor 에서 이미 reject 됐어야 함. 안전망.
            return null;
        }
        // Principal.getName() 이 String 이라 String 화. 컨트롤러에서 사용 시 Long.parseLong 으로 복원.
        final String name = userId.toString();
        return () -> name;
    }
}
