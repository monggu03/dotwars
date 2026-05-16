package com.dongguk.dotwars.game.controller;

import com.dongguk.dotwars.game.dto.FactionStatsResponse;
import com.dongguk.dotwars.game.service.FactionStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 API — 공개 라우트 (SecurityConfig 에서 /api/stats/** permitAll).
 *
 * 별도 컨트롤러로 분리한 이유:
 *  - URL prefix 가 /api/stats 로 게임 액션(/api/game)과 다름 → 같은 prefix 안 묶이는 게 자연
 *  - 추후 final_results, leaderboard 같은 통계 라우트가 늘면 한 곳에 모이기 좋음
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final FactionStatsService factionStatsService;

    @GetMapping("/factions")
    public FactionStatsResponse factions() {
        return factionStatsService.getStats();
    }
}
