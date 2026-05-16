package com.dongguk.dotwars.game.dto;

import java.time.Instant;

/**
 * POST /api/game/pixels 응답.
 *
 * 클라이언트가 받자마자 할 일:
 *  - 캔버스의 (x, y) 픽셀을 factionId 색으로 즉시 갱신 (낙관적 UI 업데이트)
 *  - cooldownEndsAt 까지 카운트다운 시작
 *  - WebSocket 으로도 같은 이벤트가 broadcast 되지만 자기 호출의 응답이 더 빠르게 도착 → 즉시 반영용
 */
public record PaintPixelResponse(
        int x,
        int y,
        Long factionId,
        Instant cooldownEndsAt
) {}
