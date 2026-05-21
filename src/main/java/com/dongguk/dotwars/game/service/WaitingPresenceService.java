package com.dongguk.dotwars.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 대기화면(/waiting.html) 현재 대기 인원 집계.
 *
 * 왜 WebSocket(SimpUserRegistry) 가 아니라 별도 presence 인가:
 *  - 대기화면은 일부러 WebSocket 핸드셰이크를 안 한다(시작 전이라 비용 아까움 — waiting.js 참고).
 *    그래서 online 카운트엔 대기자가 안 잡힌다.
 *  - 로그인 안 한 익명 방문자(QR 만 찍고 들어온 사람)도 세야 하므로, 클라이언트가 만든
 *    visitorId 를 키로 쓴다.
 *
 * 방식 — ZSET(member=visitorId, score=마지막 핑 시각ms):
 *  - 핑마다 ZADD 로 갱신, WINDOW(20초) 보다 오래된 멤버는 제거 → "최근 20초 내 살아있는 방문자 수".
 *  - 같은 사람의 여러 탭은 visitorId 가 같아 1명으로 집계.
 *  - KEYS 스캔 대신 ZSET 한 키만 사용 → O(log N) 으로 가볍다.
 */
@Service
@RequiredArgsConstructor
public class WaitingPresenceService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY = "waiting:presence";
    private static final long WINDOW_MS = 20_000L;   // 이 시간 내 핑이 있으면 "대기 중"
    private static final int MAX_ID_LEN = 64;        // 비정상적으로 긴 id 방어

    /**
     * 방문자 핑 기록 후 현재 대기 인원 반환.
     * @param visitorId 클라이언트가 생성한 고유 id (localStorage 보관)
     */
    public long touchAndCount(String visitorId) {
        String id = (visitorId == null || visitorId.isBlank()) ? "anon" : visitorId.trim();
        if (id.length() > MAX_ID_LEN) {
            id = id.substring(0, MAX_ID_LEN);
        }

        long now = System.currentTimeMillis();
        ZSetOperations<String, String> z = redisTemplate.opsForZSet();
        z.add(KEY, id, now);
        z.removeRangeByScore(KEY, 0, now - WINDOW_MS);   // 오래된 방문자 정리
        redisTemplate.expire(KEY, Duration.ofSeconds(60)); // 트래픽 끊기면 키 자체 자동 소멸

        Long count = z.zCard(KEY);
        return count == null ? 1 : count;   // 방금 본인을 add 했으니 최소 1
    }
}
