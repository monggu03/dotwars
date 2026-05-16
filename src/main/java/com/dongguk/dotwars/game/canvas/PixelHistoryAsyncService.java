package com.dongguk.dotwars.game.canvas;

import com.dongguk.dotwars.game.domain.PixelHistory;
import com.dongguk.dotwars.game.repository.PixelHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 픽셀 이력 비동기 저장.
 *
 * 호출 패턴:
 *   CanvasService.paintPixel() 끝부분에서 pixelHistoryAsyncService.save(...) 호출 →
 *   이 메서드는 별도 스레드(pixel-history-N)에서 실행 → CanvasService 는 즉시 다음 코드로 진행.
 *
 * 트랜잭션 주의:
 *   @Async 메서드는 호출자의 트랜잭션을 이어받지 못함 (별도 스레드라 영속성 컨텍스트가 다름).
 *   → @Transactional 을 이 메서드에 직접 명시. 자체 트랜잭션으로 동작.
 *
 * 자기-주입 함정:
 *   같은 클래스 안에서 this.save() 직접 호출하면 Spring AOP 프록시를 우회 → @Async 무시.
 *   외부 빈에서 호출해야만 AOP 가 적용됨.
 *   → CanvasService 가 PixelHistoryAsyncService 를 주입받아 호출하는 구조라 안전.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixelHistoryAsyncService {

    private final PixelHistoryRepository pixelHistoryRepository;

    /**
     * 픽셀 이력 1건 INSERT.
     *
     * void 반환:
     *   @Async 는 void 또는 Future 만 반환 가능. void 면 결과 무시 → "fire and forget".
     *   여기선 호출자가 결과를 알 필요 없으므로 void.
     *
     * 예외 처리:
     *   여기서 던지는 예외는 호출자에게 전달되지 않음 (별도 스레드).
     *   AsyncConfig 의 AsyncUncaughtExceptionHandler 가 로깅 → 디버깅 추적 가능.
     *   메인 흐름(사용자 응답) 은 영향 받지 않음.
     */
    @Async("taskExecutor")
    @Transactional
    public void save(Long userId, int x, int y, Long factionId) {
        PixelHistory ph = PixelHistory.builder()
                .userId(userId)
                .x(x)
                .y(y)
                .factionId(factionId)
                .build();
        pixelHistoryRepository.save(ph);
        log.debug("[history] saved userId={} ({},{}) faction={}", userId, x, y, factionId);
    }
}
