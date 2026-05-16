package com.dongguk.dotwars.game.dto;

import java.time.Instant;

/**
 * GET /api/game/cooldown 응답.
 *
 * remainingSec: 남은 초. 0 이면 지금 칠하기 가능.
 * endsAt:       쿨다운이 끝나는 절대 시각 (UTC).
 *   - 클라이언트 시계가 서버와 약간 어긋나도 endsAt 기준으로 카운트다운 가능.
 *   - 0 이면 endsAt 도 같은 now 시각 (혹은 null 도 무방). 클라이언트는 remainingSec 만 보면 됨.
 */
public record CooldownResponse(
        int remainingSec,
        Instant endsAt
) {}
