package com.dongguk.dotwars.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /api/game/pixels 요청.
 *
 * 좌표 범위: 0..49 (50x50 캔버스).
 * 하드코딩 한 이유: 캔버스 크기는 운영 중 절대 안 바뀜 (DB 시드/Redis 데이터/클라이언트가 모두 50 가정).
 * 변경하려면 시드 데이터부터 application.yml 까지 동시 갱신 필요한 결정.
 *
 * 검증 실패 → MethodArgumentNotValidException → GlobalExceptionHandler 가 400 + fieldErrors 응답.
 */
public record PaintPixelRequest(
        @Min(value = 0, message = "x는 0 이상이어야 합니다")
        @Max(value = 49, message = "x는 49 이하여야 합니다")
        int x,

        @Min(value = 0, message = "y는 0 이상이어야 합니다")
        @Max(value = 49, message = "y는 49 이하여야 합니다")
        int y
) {}
