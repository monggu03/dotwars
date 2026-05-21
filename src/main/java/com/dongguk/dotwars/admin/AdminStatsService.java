package com.dongguk.dotwars.admin;

import com.dongguk.dotwars.admin.dto.AdminStatsResponse;
import com.dongguk.dotwars.admin.dto.CellCount;
import com.dongguk.dotwars.admin.dto.HourBucket;
import com.dongguk.dotwars.admin.dto.RecentPaint;
import com.dongguk.dotwars.game.repository.PixelHistoryRepository;
import com.dongguk.dotwars.game.service.FactionStatsService;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 통계 집계.
 * 게임 통계(FactionStatsService)는 재활용하고, 운영 지표(접속/부하/격전지/시간대/최근)는 직접 집계.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final SimpUserRegistry userRegistry;
    private final UserRepository userRepository;
    private final PixelHistoryRepository pixelHistoryRepository;
    private final FactionStatsService factionStatsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        int online = userRegistry.getUserCount();

        // 최대 동시 접속 — PeakOnlineTracker 가 1분마다 갱신. 현재값이 더 크면 그걸로 보정.
        String peakStr = redisTemplate.opsForValue().get(PeakOnlineTracker.PEAK_KEY);
        int peak = Math.max(online, peakStr != null ? parseOrZero(peakStr) : 0);

        long totalPaints = pixelHistoryRepository.count();
        long participants = userRepository.countByDepartmentIsNotNull();
        long paintsLastMinute = pixelHistoryRepository.countByCreatedAtAfter(LocalDateTime.now().minusMinutes(1));

        List<CellCount> cells = pixelHistoryRepository.findCellCounts().stream()
                .map(c -> new CellCount(c.getX(), c.getY(), c.getCnt()))
                .toList();

        List<HourBucket> hourly = pixelHistoryRepository.findHourlyActivity().stream()
                .map(h -> new HourBucket(h.getBucket(), h.getCnt()))
                .toList();

        List<RecentPaint> recent = pixelHistoryRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(p -> new RecentPaint(p.getX(), p.getY(), p.getFactionId(), p.getCreatedAt()))
                .toList();

        return new AdminStatsResponse(
                online,
                peak,
                totalPaints,
                participants,
                paintsLastMinute,
                factionStatsService.getStats().factions(),
                cells,
                hourly,
                recent
        );
    }

    private int parseOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
