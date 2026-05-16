package com.dongguk.dotwars.game.dto;

import java.time.Instant;

/**
 * 세션(1일분) 요약. currentSession / nextSession 양쪽에 동일 형식으로 사용.
 *
 * 시간은 Instant (UTC) 로 직렬화 → 클라이언트가 시간대 자유롭게 변환.
 * KST LocalDateTime 으로 저장된 값은 서비스 레이어에서 systemDefault 시간대로
 * 변환해 Instant 로 노출.
 */
public record SessionInfo(
        int dayNumber,
        Instant startsAt,
        Instant endsAt
) {}
