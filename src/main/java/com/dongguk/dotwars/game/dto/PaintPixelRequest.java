package com.dongguk.dotwars.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /api/game/pixels 요청.
 *
 * 좌표 범위: 0 이상의 합리적인 정수.
 * 정확한 상한은 CanvasService 가 application.yml 의 game.canvas.width/height 로 검증 → 422.
 * 여기서는 음수/극단값 차단만 (sanity check) — @Max 는 캔버스 크기 변경 시 같이 손대지 않게 충분히 크게.
 *
 * 검증 실패 → MethodArgumentNotValidException → GlobalExceptionHandler 가 400 + fieldErrors 응답.
 */
public record PaintPixelRequest(
        @Min(value = 0, message = "x는 0 이상이어야 합니다")
        @Max(value = 999, message = "x가 너무 큽니다")
        int x,

        @Min(value = 0, message = "y는 0 이상이어야 합니다")
        @Max(value = 999, message = "y가 너무 큽니다")
        int y
) {}
