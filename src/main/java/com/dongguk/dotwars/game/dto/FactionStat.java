package com.dongguk.dotwars.game.dto;

/**
 * 진영 1개의 통계 — id/name/colorHex 는 마스터에서, pixelCount/percentage/rank 는 계산값.
 *
 * percentage 는 0.0 ~ 100.0 범위의 double. 두 자리 소수까지 노출 가능하도록 round 처리.
 * rank 는 픽셀 수 내림차순 (1위가 가장 많이 칠한 진영). 동률 처리는 일단 동일 rank 부여하지
 * 않고 정렬 순서대로 1~5 부여 — 학습 단계 단순화.
 */
public record FactionStat(
        Long id,
        String name,
        String colorHex,
        int pixelCount,
        double percentage,
        int rank
) {}
