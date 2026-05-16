package com.dongguk.dotwars.game.canvas;

import java.time.Instant;

/**
 * paintPixel 의 내부 결과 — 서비스 ↔ 컨트롤러 사이.
 * 공개 API DTO(PaintPixelResponse) 는 이 중 일부만 노출.
 *
 * prevFactionId: 이전 픽셀이 어떤 진영이었는지 (null = 흰 픽셀)
 *   - WebSocket broadcast 에서 "어디서 어디로 색 바뀜" 같은 정보 전달에 활용 가능 (다음 단계)
 */
public record PixelPaintResult(
        int x,
        int y,
        Long factionId,
        Long prevFactionId,
        Instant cooldownEndsAt
) {}
