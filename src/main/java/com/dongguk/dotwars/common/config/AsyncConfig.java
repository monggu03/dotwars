package com.dongguk.dotwars.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 작업 설정.
 *
 * 픽셀 칠하기 흐름의 핵심 결정:
 *   사용자 응답에는 영향 없이 "픽셀 이력(pixel_history) INSERT" 를 백그라운드에서 처리.
 *
 *   왜:
 *   - 픽셀 칠하기 API 응답은 Redis 업데이트만 끝나면 충분 (실시간 캔버스에 반영)
 *   - DB INSERT 는 통계/감사용 → 즉시 반영 안 돼도 무관
 *   - INSERT 를 동기로 두면 매 요청에 DB I/O 추가 → 응답 지연 + 동시성 부하 증가
 *
 *   Trade-off:
 *   - DB INSERT 실패 시 그 픽셀 이력이 영구 손실 (큐가 메모리에 있으므로 서버 죽으면 잃음)
 *   - 실서비스에선 Kafka/RabbitMQ 같은 영속 큐를 쓰지만 학습 단계엔 ThreadPool 이면 충분.
 *
 * 풀 크기 결정 근거:
 *   - 픽셀 칠하기 동시 처리량 추정: 축제 1000명 × 평균 5분 쿨다운 → 초당 ~3-4 픽셀
 *   - DB INSERT 1건 약 10-50ms → corePool 4 면 충분히 처리
 *   - 짧은 스파이크 대비 maxPool 10 + queueCapacity 100 으로 버퍼 확보
 *   - 100개 큐 가득 차면 새 작업은 거부(reject) — 그 픽셀 이력만 누락, 메인 흐름은 무사
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * @Async("taskExecutor") 에서 참조하는 빈 이름.
     * "taskExecutor" 라는 이름은 Spring 의 기본 비동기 풀 이름과 같음 → @EnableAsync 가 자동 사용.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);            // 평소 유지하는 스레드 수
        executor.setMaxPoolSize(10);            // 부하 시 일시 확장 가능한 상한
        executor.setQueueCapacity(100);         // core 가 다 차면 대기열로
        executor.setThreadNamePrefix("pixel-history-");  // 로그에서 "pixel-history-3" 식으로 식별
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 종료 시 남은 작업 처리 후 종료
        executor.setAwaitTerminationSeconds(30);             // 최대 30초까지 기다리고 강제 종료
        executor.initialize();
        return executor;
    }

    /**
     * @Async 메서드가 던진 예외는 호출자에게 안 전달됨 (별도 스레드).
     * 이 핸들러가 못 잡은 예외를 로깅 — 디버깅 추적성 확보.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("[async] 비동기 작업 실패: method={} params={} msg={}",
                        method.getName(), params, throwable.getMessage(), throwable);
    }
}
