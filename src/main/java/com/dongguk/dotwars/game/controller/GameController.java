package com.dongguk.dotwars.game.controller;

import com.dongguk.dotwars.game.canvas.CanvasService;
import com.dongguk.dotwars.game.canvas.PixelPaintResult;
import com.dongguk.dotwars.game.dto.CanvasResponse;
import com.dongguk.dotwars.game.dto.CooldownResponse;
import com.dongguk.dotwars.game.dto.PaintPixelRequest;
import com.dongguk.dotwars.game.dto.PaintPixelResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 게임 액션 API.
 *
 * 라우트:
 *  - GET  /api/game/canvas     — 캔버스 전체 상태 (공개)
 *  - GET  /api/game/cooldown   — 본인 쿨다운 남은 시간 (인증 필요)
 *  - POST /api/game/pixels     — 픽셀 1개 칠하기 (인증 필요)
 *
 * SecurityConfig 의 매처:
 *   /api/game/canvas, /api/game/status 는 permitAll. /api/game/cooldown, /api/game/pixels 는
 *   anyRequest().authenticated() 로 떨어져 JWT 필요.
 */
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final CanvasService canvasService;

    /**
     * GET /api/game/canvas — 캔버스 전체 상태.
     * 비로그인 사용자도 구경 가능 (게임 상태 공개).
     */
    @GetMapping("/canvas")
    public CanvasResponse canvas() {
        int[][] pixels = canvasService.getCurrentCanvas();
        return new CanvasResponse(
                canvasService.getCanvasWidth(),
                canvasService.getCanvasHeight(),
                pixels,
                Instant.now()
        );
    }

    /**
     * GET /api/game/cooldown — 본인 쿨다운 남은 시간.
     * @AuthenticationPrincipal 로 JWT 필터가 박은 userId 를 받음.
     */
    @GetMapping("/cooldown")
    public CooldownResponse cooldown(@AuthenticationPrincipal Long userId) {
        int remaining = canvasService.getCooldownRemainingSec(userId);
        Instant endsAt = Instant.now().plusSeconds(remaining);
        return new CooldownResponse(remaining, endsAt);
    }

    /**
     * POST /api/game/pixels — 픽셀 1개 칠하기.
     *
     * 흐름:
     *  1) @Valid 가 좌표 범위(0..49) 검사 — 실패 시 400
     *  2) CanvasService.paintPixel() 가 게임 상태/쿨다운/Redis 업데이트
     *  3) 성공 시 PaintPixelResponse 반환 — 클라이언트는 즉시 UI 반영
     *
     * 예외:
     *  - 게임 비활성: 403 GAME_NOT_ACTIVE
     *  - 쿨다운 중:  429 COOLDOWN_ACTIVE + remainingSec
     *  - 잘못된 좌표: 400 (검증 핸들러)
     */
    @PostMapping("/pixels")
    public PaintPixelResponse paint(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaintPixelRequest request
    ) {
        PixelPaintResult result = canvasService.paintPixel(userId, request.x(), request.y());
        return new PaintPixelResponse(
                result.x(),
                result.y(),
                result.factionId(),
                result.cooldownEndsAt()
        );
    }
}
