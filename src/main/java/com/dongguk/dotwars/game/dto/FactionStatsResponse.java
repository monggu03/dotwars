package com.dongguk.dotwars.game.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/stats/factions 응답.
 *
 * factions: 랭킹 순으로 정렬됨 (1위가 첫 원소).
 * totalPixels: 캔버스 전체 칸 수. 50x50 = 2500. 비율 계산의 분모.
 * whitePixels: 아직 칠해지지 않은 흰색 칸 수 (totalPixels - sum(pixelCount)).
 * calculatedAt: 통계 계산 시각. 캐시된 응답이면 캐시 작성 시각.
 */
public record FactionStatsResponse(
        List<FactionStat> factions,
        int totalPixels,
        int whitePixels,
        Instant calculatedAt
) {}
