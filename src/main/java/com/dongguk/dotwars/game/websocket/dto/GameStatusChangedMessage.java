package com.dongguk.dotwars.game.websocket.dto;

import com.dongguk.dotwars.game.dto.SessionInfo;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * /topic/game 브로드캐스트 메시지 — 게임 라이프사이클 전환.
 *
 * GameStatusResponse(REST API) 와 의도적으로 같은 형태:
 *  - 클라이언트가 폴링 응답과 WebSocket 메시지를 같은 코드 경로로 처리할 수 있음.
 *  - 단 type 필드만 추가 — 채널 내 이벤트 분기용.
 *
 * 발행 시점: GameScheduler 가 매초 상태 계산 후 Redis 값이 바뀐 그 1회만.
 *   ACTIVE → FROZEN, FROZEN → ACTIVE, * → ENDED 등.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStatusChangedMessage(
        String type,
        String status,
        SessionInfo currentSession,
        SessionInfo nextSession,
        Instant timestamp
) {
    public static GameStatusChangedMessage of(String status,
                                              SessionInfo current,
                                              SessionInfo next) {
        return new GameStatusChangedMessage(
                "GAME_STATUS_CHANGED", status, current, next, Instant.now()
        );
    }
}
