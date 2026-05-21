package com.dongguk.dotwars.game.service;

import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.repository.FactionRepository;
import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.dto.FactionStat;
import com.dongguk.dotwars.game.dto.FactionStatsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 진영 통계 서비스 — Redis 진영 카운트를 진영 마스터와 매핑 + 랭킹/비율 계산.
 *
 * 캐시 설계 (Redis 1초 TTL):
 *   1000명이 3초 간격으로 polling 하면 초당 약 333 요청.
 *   각 요청마다 HGETALL + 5건 SELECT factions + 계산을 다 돌면 부하 큼.
 *   → 1초간 결과 캐시 → 실제 계산은 초당 1번. 사용자 체감 차이 무시 가능 (3초 폴링이라).
 *
 * 캐시 Stampede (의도적으로 단순화):
 *   1초 만료 직후에 여러 요청이 동시에 와서 모두 캐시 미스 → 모두 직접 계산 → DB/Redis 부하 일시 ↑.
 *   진짜 스템피드 방어는 SETNX 락 또는 단일 갱신 워커가 정석이지만, 1초 TTL 에서 동시 미스 수는
 *   많아야 수십 건이고 계산이 O(5) 라 무시 가능. 학습 단계에 과한 복잡도.
 *
 * @Cacheable 대신 직접 구현한 이유:
 *  - @Cacheable 은 Cache 추상화 빈 등록 + 어노테이션 의미 학습이 별도로 필요.
 *  - 직접 짜면 GET/SET TTL 의 동작이 코드에 그대로 보임 → 학습 가치.
 *  - 운영 단계에서 Caffeine/Ehcache 같은 로컬 캐시 추가하면 그 때 @Cacheable 도입 검토.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FactionStatsService {

    private static final String CACHE_KEY = "stats:factions:cache";
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final FactionRepository factionRepository;
    private final ObjectMapper objectMapper;

    @Value("${game.canvas.width:11}")
    private int canvasWidth;

    @Value("${game.canvas.height:17}")
    private int canvasHeight;

    public FactionStatsResponse getStats() {
        // 1) 캐시 조회
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                log.debug("[stats] cache HIT");
                return objectMapper.readValue(cached, FactionStatsResponse.class);
            } catch (JsonProcessingException e) {
                // 캐시 파싱 실패 (스키마 변경 등). 그냥 새로 계산하면 됨.
                log.warn("[stats] 캐시 역직렬화 실패, 재계산 진행: {}", e.getMessage());
            }
        }

        // 2) 캐시 미스 — 실제 계산
        log.debug("[stats] cache MISS, recalculating");
        FactionStatsResponse fresh = calculate();

        // 3) 캐시 저장 (1초 TTL)
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(fresh), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("[stats] 캐시 직렬화 실패 (응답은 정상 반환): {}", e.getMessage());
        }
        return fresh;
    }

    /**
     * 실제 계산. 호출 비용:
     *  - Redis HGETALL faction:count (1 round-trip)
     *  - DB SELECT factions (1 SELECT, 5 행)
     *  - 인메모리 매핑 + 정렬 (O(N log N), N=5)
     */
    private FactionStatsResponse calculate() {
        // 진영별 픽셀 카운트 (Redis)
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(CanvasRedisKeys.FACTION_COUNT);
        Map<Long, Integer> countMap = new HashMap<>(raw.size());
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            // value 가 음수가 되면 안 되지만 Redis 데이터 오염 대비 음수도 안전하게 처리.
            countMap.put(Long.parseLong((String) e.getKey()), Integer.parseInt((String) e.getValue()));
        }

        // 진영 마스터 (DB) — 시드된 5개. 변경 거의 없어 매번 조회해도 부담 적음.
        List<Faction> factions = factionRepository.findAll();
        int totalPixels = canvasWidth * canvasHeight;

        // FactionStat 만들기 (rank 는 정렬 후 부여)
        List<FactionStat> stats = new ArrayList<>(factions.size());
        int sumPainted = 0;
        for (Faction f : factions) {
            int count = countMap.getOrDefault(f.getId(), 0);
            sumPainted += count;
            double pct = totalPixels == 0 ? 0.0 : roundTo2(count * 100.0 / totalPixels);
            // rank=0 placeholder, 아래에서 채움
            stats.add(new FactionStat(f.getId(), f.getName(), f.getColorHex(), count, pct, 0));
        }

        // 픽셀 수 내림차순 → 1위부터 rank 부여. 같은 수면 정렬 안정성에 따라 임의 — 동률은
        // 운영 후 정책 결정 후 구현 (현재는 단순화).
        stats.sort(Comparator.comparingInt(FactionStat::pixelCount).reversed()
                .thenComparing(FactionStat::id));   // 동률 시 id 오름차순으로 결정적 정렬

        List<FactionStat> ranked = new ArrayList<>(stats.size());
        for (int i = 0; i < stats.size(); i++) {
            FactionStat s = stats.get(i);
            ranked.add(new FactionStat(s.id(), s.name(), s.colorHex(),
                    s.pixelCount(), s.percentage(), i + 1));
        }

        int whitePixels = Math.max(totalPixels - sumPainted, 0);
        return new FactionStatsResponse(ranked, totalPixels, whitePixels, Instant.now());
    }

    private static double roundTo2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
