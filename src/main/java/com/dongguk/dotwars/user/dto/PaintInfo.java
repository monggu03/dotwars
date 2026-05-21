package com.dongguk.dotwars.user.dto;

import java.time.LocalDateTime;

/**
 * 본인이 칠한 픽셀 1개 — 마이페이지 리스트의 한 row.
 *
 *  - x, y       : 캔버스 좌표
 *  - paintedAt  : 칠한 시각 (createdAt)
 *  - alive      : 현재 그 셀이 여전히 내 진영 색인가
 *                 (false = 다른 진영이 덮었거나, 같은 진영 사람이 다시 칠한 경우 등)
 */
public record PaintInfo(
        int x,
        int y,
        LocalDateTime paintedAt,
        boolean alive
) {}
