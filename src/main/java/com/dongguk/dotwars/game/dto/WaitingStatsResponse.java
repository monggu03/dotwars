package com.dongguk.dotwars.game.dto;

import java.util.List;

/**
 * 대기화면 인원 통계 — 총 가입자 + 진영별 가입자.
 * "현재 N명 대기 중!" 헤드라인 + 진영별 스트립에 사용.
 * 표시용 기준값(+25 / 진영당 +5)은 프론트에서 더함 — 백엔드는 실제 수치만 반환.
 */
public record WaitingStatsResponse(long total, List<FactionWaiting> factions) {

    /** 진영 1개의 대기 인원. displayOrder 순으로 정렬되어 내려감. */
    public record FactionWaiting(Long id, String name, String colorHex, long count) {}
}
