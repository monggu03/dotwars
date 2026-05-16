package com.dongguk.dotwars.game.websocket.dto;

import java.time.Instant;

/**
 * /topic/canvas 브로드캐스트 메시지 — 누군가 픽셀 1개를 칠했음.
 *
 * type 필드:
 *  - 단일 채널에서 여러 종류 이벤트를 보낼 때 클라이언트가 분기하기 위함.
 *  - 현재는 PIXEL_PAINTED 한 종류뿐이지만, 추후 PIXEL_RESET 같은 이벤트 추가 시 호환 유지.
 *
 * timestamp: 서버 발행 시각 (UTC Instant). 클라이언트가 도착 지연 감지/통계용으로 활용 가능.
 */
public record PixelPaintedMessage(
        String type,
        int x,
        int y,
        Long factionId,
        Instant timestamp
) {
    public static PixelPaintedMessage of(int x, int y, Long factionId) {
        return new PixelPaintedMessage("PIXEL_PAINTED", x, y, factionId, Instant.now());
    }
}
