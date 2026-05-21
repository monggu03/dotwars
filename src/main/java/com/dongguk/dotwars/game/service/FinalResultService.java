package com.dongguk.dotwars.game.service;

import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.repository.FactionRepository;
import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.domain.FinalResult;
import com.dongguk.dotwars.game.repository.FinalResultRepository;
import com.dongguk.dotwars.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게임 종료 시 최종 결과(final_results) INSERT.
 *
 * 호출 시점: GameScheduler 가 ACTIVE → ENDED 전환 감지하는 순간 한 번.
 *
 * 멱등성:
 *   - 이미 final_results 행이 있으면 스킵.
 *   - 재시작 후 스케줄러가 다시 ENDED 감지해도 중복 INSERT 안 됨.
 *
 * @Transactional 분리 이유:
 *   - GameScheduler 의 @Scheduled 메서드는 매초 호출 → 매번 트랜잭션 시작/커밋 비용 큼.
 *   - DB 쓰기가 정말 필요한 경로(ENDED 첫 진입)에만 트랜잭션 박는 게 효율적.
 *   - 별도 서비스로 분리하면 self-invocation 함정도 회피 (Spring AOP 프록시 적용 보장).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinalResultService {

    private final RedisTemplate<String, String> redisTemplate;
    private final FactionRepository factionRepository;
    private final GameRepository gameRepository;
    private final FinalResultRepository finalResultRepository;

    @Value("${game.canvas.width:11}")
    private int canvasWidth;

    @Value("${game.canvas.height:17}")
    private int canvasHeight;

    /**
     * 최종 결과 계산 + INSERT (한 번만 동작).
     *
     * 멱등성 보장:
     *   finalResultRepository.count() > 0 이면 즉시 스킵.
     *   단일 인스턴스 가정 — 다중 인스턴스 환경에선 DB UNIQUE 제약 + ON DUPLICATE 패턴 필요.
     */
    @Transactional
    public void calculateAndSaveIfAbsent() {
        if (finalResultRepository.count() > 0) {
            log.info("[final-result] 이미 존재 → 스킵");
            return;
        }
        Long gameId = gameRepository.findAll().stream()
                .map(g -> g.getId())
                .findFirst()
                .orElse(null);
        if (gameId == null) {
            log.warn("[final-result] game 미존재 — 계산 스킵");
            return;
        }

        // Redis 진영 카운트 → Map<factionId, count>
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(CanvasRedisKeys.FACTION_COUNT);
        Map<Long, Integer> counts = new HashMap<>(raw.size());
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            counts.put(Long.parseLong((String) e.getKey()),
                    Integer.parseInt((String) e.getValue()));
        }

        // 진영 마스터 (5개) 가져와 카운트 미존재 진영도 0 으로 포함.
        // 모든 진영에 대해 행 생성 → 결과 화면에서 0 점도 보여주는 게 자연.
        List<Faction> factions = factionRepository.findAll();
        int totalPixels = canvasWidth * canvasHeight;
        LocalDateTime calculatedAt = LocalDateTime.now();

        // 픽셀 수 내림차순 정렬 후 rank 부여 (동률은 factionId 오름차순으로 결정적 정렬)
        List<Faction> sorted = new ArrayList<>(factions);
        sorted.sort(Comparator.<Faction>comparingInt(f -> -counts.getOrDefault(f.getId(), 0))
                .thenComparing(Faction::getId));

        List<FinalResult> rows = new ArrayList<>(sorted.size());
        int rank = 1;
        for (Faction f : sorted) {
            int count = counts.getOrDefault(f.getId(), 0);
            double pct = totalPixels == 0 ? 0.0 : Math.round(count * 10000.0 / totalPixels) / 100.0;
            rows.add(FinalResult.builder()
                    .gameId(gameId)
                    .factionId(f.getId())
                    .pixelCount(count)
                    .percentage(pct)
                    .rankPosition(rank++)
                    .calculatedAt(calculatedAt)
                    .build());
        }
        finalResultRepository.saveAll(rows);
        log.info("[final-result] 저장 완료 — {} 행, 1위 factionId={}",
                rows.size(), rows.isEmpty() ? null : rows.get(0).getFactionId());
    }
}
