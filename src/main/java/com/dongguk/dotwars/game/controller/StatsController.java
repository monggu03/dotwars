package com.dongguk.dotwars.game.controller;

import com.dongguk.dotwars.game.dto.FactionStatsResponse;
import com.dongguk.dotwars.game.repository.PixelHistoryRepository;
import com.dongguk.dotwars.game.service.FactionStatsService;
import com.dongguk.dotwars.game.service.WaitingPresenceService;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 통계 API — 공개 라우트 (SecurityConfig 에서 /api/stats/** permitAll).
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final FactionStatsService factionStatsService;
    private final SimpUserRegistry userRegistry;
    private final UserRepository userRepository;
    private final PixelHistoryRepository pixelHistoryRepository;
    private final WaitingPresenceService waitingPresenceService;

    @GetMapping("/factions")
    public FactionStatsResponse factions() {
        return factionStatsService.getStats();
    }

    /**
     * 하단 ticker 용 통합 통계.
     *  - online : 현재 WebSocket 으로 연결된 고유 사용자 수 (SimpUserRegistry, Principal=userId 기준)
     *  - participants : 단과대까지 선택해 게임에 참여한 누적 사용자 수
     *  - paints : 지금까지 칠해진 픽셀의 누적 횟수 (pixel_history row count, 덮어쓰기 포함)
     *
     * 한 endpoint 로 묶어 ticker 가 1회 fetch 로 모든 데이터 받게 함. 10초 폴링.
     */
    @GetMapping("/online")
    public Map<String, Long> online() {
        return Map.of(
                "online", (long) userRegistry.getUserCount(),
                "participants", userRepository.countByDepartmentIsNotNull(),
                "paints", pixelHistoryRepository.count()
        );
    }

    /**
     * 대기화면 현재 대기 인원. waiting.js 가 10초마다 호출(핑 겸 조회).
     * WebSocket 을 안 쓰는 대기화면 전용 presence — 자세한 이유는 WaitingPresenceService 참고.
     *
     * @param v 클라이언트가 생성한 고유 visitorId (익명 방문자 포함)
     */
    @GetMapping("/waiting")
    public Map<String, Long> waiting(@RequestParam("v") String v) {
        return Map.of("waiting", waitingPresenceService.touchAndCount(v));
    }
}
