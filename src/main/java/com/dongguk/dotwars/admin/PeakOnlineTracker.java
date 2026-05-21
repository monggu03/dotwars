package com.dongguk.dotwars.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 최대 동시 접속자 기록.
 *
 * SimpUserRegistry.getUserCount() 는 "현재값" 만 줌 → 1분마다 샘플링해 Redis 에 최댓값 누적.
 * 관리자 대시보드의 "최대 동시 접속" 지표에 사용.
 *
 * 1분 주기 이유: 접속자 수는 분 단위로 충분. 매초 샘플링은 과함.
 * 데이터 초기화(reset-game-data) 시엔 안 지워짐 — 필요하면 수동 DEL stats:peak_online.
 */
@Component
@RequiredArgsConstructor
public class PeakOnlineTracker {

    public static final String PEAK_KEY = "stats:peak_online";

    private final SimpUserRegistry userRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedRate = 60_000)
    public void sample() {
        int current = userRegistry.getUserCount();
        String prev = redisTemplate.opsForValue().get(PEAK_KEY);
        int peak = (prev != null) ? parseOrZero(prev) : 0;
        if (current > peak) {
            redisTemplate.opsForValue().set(PEAK_KEY, String.valueOf(current));
        }
    }

    private int parseOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
