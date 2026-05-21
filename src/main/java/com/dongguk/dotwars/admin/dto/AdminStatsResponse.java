package com.dongguk.dotwars.admin.dto;

import com.dongguk.dotwars.game.dto.FactionStat;

import java.util.List;

/**
 * 관리자 대시보드 통합 응답.
 *
 *  - online / peakOnline   : 현재 / 최대 동시 접속
 *  - totalPaints           : 누적 페인트 수
 *  - participants          : 단과대 선택 완료 사용자 수
 *  - paintsLastMinute      : 최근 1분 페인트 수 (= 현재 부하 지표)
 *  - factions              : 진영별 통계 (점유율/순위)
 *  - cells                 : 칠해진 칸들 (count 내림차순) — 프론트가 top10 격전지 + 히트맵으로 사용
 *  - hourly                : 시간대별 활동량
 *  - recent                : 최근 페인트 로그
 */
public record AdminStatsResponse(
        int online,
        int peakOnline,
        long totalPaints,
        long participants,
        long paintsLastMinute,
        List<FactionStat> factions,
        List<CellCount> cells,
        List<HourBucket> hourly,
        List<RecentPaint> recent
) {}
