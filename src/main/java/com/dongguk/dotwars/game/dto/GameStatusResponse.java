package com.dongguk.dotwars.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * GET /api/game/status 응답.
 *
 * status: ACTIVE / FROZEN / SCHEDULED / ENDED — Redis game:status 값 그대로.
 * currentSession: ACTIVE 일 때만 채워짐. 다른 상태에선 null.
 * nextSession:    FROZEN / SCHEDULED 일 때 다음 세션 안내. ACTIVE / ENDED 면 null.
 * serverTime:     클라이언트 시각 동기화용. 카운트다운 UI 가 클라이언트 시계 오차에도 정확하게 동작하도록.
 *
 * @JsonInclude(NON_NULL): currentSession / nextSession 이 null 인 경우 JSON 에서 키 자체 제외.
 *   → 응답 페이로드 작아지고 클라이언트는 "null vs 키 없음" 어느 경우든 동일 처리 가능.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStatusResponse(
        String status,
        SessionInfo currentSession,
        SessionInfo nextSession,
        Instant serverTime
) {}
