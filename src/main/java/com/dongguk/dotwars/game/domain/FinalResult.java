package com.dongguk.dotwars.game.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 최종 결과(FinalResult) — 게임 종료 시점의 진영별 점유율 집계 결과.
 *
 * 왜 별도 테이블에 적재하는가:
 *  - 게임 종료 후 결과는 절대 바뀌면 안 됨. 매번 PixelHistory/Redis 로부터 재계산하면 시점 일관성 위험.
 *  - 종료 시점에 한 번 집계 → 영구 보관. 이후 통계 API 는 이 테이블만 읽음 → 빠르고 안정적.
 *  - "Day 1 vs Day 2 vs 최종" 같은 스냅샷 비교도 추후 동일 패턴으로 추가 가능.
 */
@Entity
@Table(name = "final_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PixelHistory 와 마찬가지로 객체 매핑이 아닌 raw id. 집계 결과 행이라 그래프 탐색 불필요.
    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "faction_id", nullable = false)
    private Long factionId;

    // 해당 진영이 차지한 픽셀 수. 50x50=2500 픽셀이 상한.
    @Column(name = "pixel_count", nullable = false)
    private int pixelCount;

    // 점유율(0.0 ~ 100.0). 화면 표시용 정확도면 double 로 충분.
    // 정밀한 금액 계산이라면 BigDecimal 을 쓰지만 표시용 백분율엔 double 의 오차가 무시할 수준.
    @Column(nullable = false)
    private double percentage;

    // 1, 2, 3, 4, 5 — 최종 순위. 동률 처리 정책은 집계 서비스에서 결정.
    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    // 이 결과를 적재한 시각. @CreationTimestamp 가 아닌 명시적 LocalDateTime — 게임 종료 시각과 일치시키고 싶을 수 있어서.
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Builder
    private FinalResult(Long gameId, Long factionId, int pixelCount,
                        double percentage, int rankPosition, LocalDateTime calculatedAt) {
        this.gameId = gameId;
        this.factionId = factionId;
        this.pixelCount = pixelCount;
        this.percentage = percentage;
        this.rankPosition = rankPosition;
        this.calculatedAt = calculatedAt;
    }
}
