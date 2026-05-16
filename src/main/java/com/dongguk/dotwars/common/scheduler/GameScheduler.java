package com.dongguk.dotwars.common.scheduler;

import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.domain.GameStatus;
import com.dongguk.dotwars.game.repository.GameSessionRepository;
import com.dongguk.dotwars.game.service.FinalResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 게임 라이프사이클 자동 전환 스케줄러.
 *
 * 동작:
 *  - 매초 현재 시각 기준 게임 상태 계산
 *  - Redis 의 game:status 와 다르면 갱신 + 전환 로그
 *  - ACTIVE → ENDED 전환 시 final_results 계산 (FinalResultService 위임)
 *
 * 왜 매초인가:
 *  - 24:00 정각에 정확히 ACTIVE → FROZEN 전환되려면 sub-second 정확도가 필요한 건 아니지만
 *    1초 단위 정밀도면 사용자 체감에서 자연. 16:00 도래 시점도 마찬가지.
 *  - 더 길게(예: 10초) 잡으면 시작/종료 시각 부근에서 최대 10초까지 지연 발생.
 *
 * 분산 환경 고려:
 *  - 현재 단일 EC2 가정 → 단일 인스턴스가 스케줄러 돌리면 충돌 없음.
 *  - 다중 인스턴스로 가면 모든 인스턴스가 매초 동시에 갱신 시도 → Redis SET 은 마지막 쓰기가 이김.
 *    상태 전환 로그가 여러 인스턴스에서 중복 출력 + final_results INSERT 가 동시 시도될 위험.
 *  - 운영 단계 분산 시 Quartz Cluster 또는 ShedLock 등 분산 락 도입 필요.
 *
 * 스케줄러 실패 시:
 *  - Spring Scheduler 는 다음 주기에 자동 재시도 (별도 실패 처리 불필요).
 *  - 다만 예외가 매번 발생하면 로그가 폭주하므로 try/catch 로 swallow + 로그.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameScheduler {

    private final RedisTemplate<String, String> redisTemplate;
    private final GameSessionRepository gameSessionRepository;
    private final FinalResultService finalResultService;

    /**
     * 매초 tick. fixedRate = 이전 호출 시작으로부터 1000ms 후 다음 호출.
     * 만약 한 tick 이 1000ms 보다 오래 걸리면 다음 tick 은 그 종료 직후 즉시 실행.
     */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String currentStatus = redisTemplate.opsForValue().get(CanvasRedisKeys.GAME_STATUS);
            String newStatus = computeStatus(now);

            if (!newStatus.equals(currentStatus)) {
                redisTemplate.opsForValue().set(CanvasRedisKeys.GAME_STATUS, newStatus);
                log.info("[scheduler] 상태 전환: {} → {}", currentStatus, newStatus);

                // ACTIVE → ENDED 전환을 감지한 첫 tick 에서만 최종 결과 계산.
                // FinalResultService.calculateAndSaveIfAbsent() 가 멱등성 보장하므로
                // 중복 호출돼도 안전.
                if (GameStatus.ENDED.name().equals(newStatus)) {
                    finalResultService.calculateAndSaveIfAbsent();
                }
                // STEP 6 에서 WebSocket 으로 상태 변경 브로드캐스트 추가 예정.
            }
        } catch (Exception e) {
            // 매초 호출되는 메서드라 예외 폭주 위험 → swallow + 로그.
            // 다음 주기에 자동 재시도되므로 일시적 Redis 장애 등은 자가 회복.
            log.error("[scheduler] tick 실패: {}", e.getMessage());
        }
    }

    /**
     * 현재 시각 기준 게임 상태 결정.
     *  - 진행 중 세션 있음 → ACTIVE
     *  - 없지만 미래 세션 있음 → FROZEN (게임 시작 전 또는 일자 사이 휴식)
     *  - 둘 다 없음 → ENDED (모든 세션 종료)
     *
     * SCHEDULED 와 FROZEN 을 분리하는 건 클라이언트 UX 에서 큰 차이를 만들기 어려워 단순화.
     * 클라이언트가 정말 구분이 필요하면 currentSession / nextSession 의 dayNumber 보고 판단.
     */
    private String computeStatus(LocalDateTime now) {
        if (gameSessionRepository.findCurrentSession(now).isPresent()) {
            return GameStatus.ACTIVE.name();
        }
        if (gameSessionRepository.findFirstByStartsAtAfterOrderByStartsAtAsc(now).isPresent()) {
            return GameStatus.FROZEN.name();
        }
        return GameStatus.ENDED.name();
    }
}
