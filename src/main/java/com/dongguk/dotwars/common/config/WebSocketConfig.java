package com.dongguk.dotwars.common.config;

import com.dongguk.dotwars.auth.jwt.JwtHandshakeHandler;
import com.dongguk.dotwars.auth.jwt.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 설정.
 *
 * 왜 STOMP 인가 (순수 WebSocket 대비):
 *  - 순수 WebSocket 은 메시지 형식을 직접 정의해야 함 (raw frame).
 *  - STOMP 는 메시지 브로커 프로토콜 — 채널(/topic, /queue) 기반 PUB/SUB 가 표준.
 *  - Spring 통합이 우수해 @MessageMapping, SimpMessagingTemplate 등 추상화가 자연.
 *  - SockJS fallback 으로 WebSocket 미지원 환경에서도 long-polling 으로 동작.
 *
 * 메시지 흐름 (이번 step):
 *  서버 → 클라이언트: /topic/canvas, /topic/game (broadcast)
 *  클라이언트 → 서버: 사용 안 함. 픽셀 칠하기는 REST API (HTTP 상태 코드/인증 활용).
 *
 * 왜 클라 → 서버 메시지 안 쓰나:
 *  - REST 가 429/403 같은 비즈니스 상태 표현에 더 자연.
 *  - 인증/검증/응답을 REST 에서 처리하고, 결과 broadcast 만 WebSocket 에서. 관심사 분리.
 *
 * 단일 서버 한계:
 *  - SimpMessagingTemplate 은 같은 JVM 에 연결된 클라이언트에게만 전달.
 *  - 다중 서버 환경에선 서버 A 에서 보낸 메시지가 서버 B 의 클라이언트에 도달 X.
 *  - 해결: STOMP broker 를 외부(RabbitMQ) 로 빼거나, Redis Pub/Sub 로 인스턴스 간 fan-out.
 *  - 우리 운영은 단일 EC2 가정 → 이번 step 에는 단순 broker 면 충분.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버가 클라이언트로 메시지 발행하는 prefix.
        // 클라이언트는 STOMP SUBSCRIBE destination 으로 /topic/canvas 같은 경로 구독.
        registry.enableSimpleBroker("/topic");

        // 클라이언트가 @MessageMapping 핸들러로 보낼 때 prefix.
        // 이번 step 에선 안 쓰지만 표준상 등록 (추후 채팅 같은 양방향 기능 도입 시 사용).
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // HTTP 핸드셰이크 시점에 JWT 쿠키 검증 + userId attribute 저장
                .addInterceptors(jwtHandshakeInterceptor)
                // 그 userId 를 Principal 로 변환
                .setHandshakeHandler(jwtHandshakeHandler)
                // 개발 환경 CORS — 운영에선 dotwars.kr 등 도메인 명시 권장
                .setAllowedOriginPatterns("*")
                // WebSocket 미지원 환경 fallback (long-polling).
                // 모바일 일부 브라우저/사내망 프록시 대비.
                .withSockJS();
    }
}
