package com.dongguk.dotwars.user.dto;

import java.util.List;

/**
 * 마이페이지 응답 — 본인의 페인트 통계 + 최근 N개 목록.
 *
 *  - totalPaints     : 누적 칠한 횟수 (pixel_history 전체 row count)
 *  - paints          : 최근 N개 (서버 LIMIT, 기본 50). 각 row 에 alive 여부 포함
 *  - factionName     : 본인 진영명 (단과대 미선택 시 null)
 *  - factionColorHex : 진영 색 (UI 배지/타이틀용)
 */
public record UserPaintsResponse(
        long totalPaints,
        List<PaintInfo> paints,
        String factionName,
        String factionColorHex
) {}
