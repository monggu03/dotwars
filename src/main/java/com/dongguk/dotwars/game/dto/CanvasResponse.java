package com.dongguk.dotwars.game.dto;

import java.time.Instant;

/**
 * GET /api/game/canvas 응답.
 *
 * pixels: 2D 배열 — pixels[y][x] = factionId (0이면 흰색).
 *   - 행을 y, 열을 x 로 두는 이유: 클라이언트 캔버스 좌표계가 같은 방향이라 그대로 그릴 수 있음.
 *
 * snapshotAt: 응답 생성 시각 (UTC ISO 8601).
 *   - 클라이언트가 "이 시점 이후의 WebSocket 이벤트만 반영" 하면 누락/중복 없는 동기화 가능.
 *   - 단순 표시용으로도 사용 — "방금 갱신됨" 같은 UI.
 *
 * 페이로드 크기: 50×50 = 2500개 int. JSON 배열로 ~10KB 미만. 가벼움.
 */
public record CanvasResponse(
        int width,
        int height,
        int[][] pixels,
        Instant snapshotAt
) {}
