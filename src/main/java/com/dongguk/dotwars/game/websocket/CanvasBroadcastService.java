package com.dongguk.dotwars.game.websocket;

import com.dongguk.dotwars.game.websocket.dto.GameStatusChangedMessage;
import com.dongguk.dotwars.game.websocket.dto.PixelPaintedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 브로드캐스트 서비스 — 모든 구독자에게 캔버스/게임 이벤트 발행.
 *
 * 두 채널:
 *  - /topic/canvas : 픽셀 칠하기 결과 (PIXEL_PAINTED)
 *  - /topic/game   : 게임 상태 전환 (GAME_STATUS_CHANGED)
 *
 * 호출자:
 *  - CanvasService.paintPixel() 성공 시 → broadcastPixelPainted
 *  - GameScheduler 가 상태 전환 감지 시 → broadcastGameStatusChanged
 *
 * 비동기 처리는 의도적으로 미적용:
 *  - SimpMessagingTemplate.convertAndSend() 자체가 매우 빠름 (메모리 큐 push).
 *  - 비동기로 빼면 큐 가득 차거나 스레드 전환 비용으로 오히려 지연될 수 있음.
 *  - 부하 테스트 단계에서 진짜 병목으로 잡히면 그 때 비동기 도입 검토.
 *
 * 단일 서버 한계:
 *  - 같은 JVM 의 SimpleBroker 만 사용 → 멀티 서버 환경에서 다른 인스턴스 클라이언트는 못 받음.
 *  - 다음 단계(부하 테스트 / 멀티 서버)에서 Redis Pub/Sub 로 fan-out 검토.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CanvasBroadcastService {

    private static final String TOPIC_CANVAS = "/topic/canvas";
    private static final String TOPIC_GAME = "/topic/game";

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastPixelPainted(int x, int y, Long factionId) {
        PixelPaintedMessage msg = PixelPaintedMessage.of(x, y, factionId);
        messagingTemplate.convertAndSend(TOPIC_CANVAS, msg);
        log.debug("[ws] PIXEL_PAINTED ({},{}) faction={}", x, y, factionId);
    }

    public void broadcastGameStatusChanged(GameStatusChangedMessage message) {
        messagingTemplate.convertAndSend(TOPIC_GAME, message);
        log.info("[ws] GAME_STATUS_CHANGED → {}", message.status());
    }
}
