package com.dongguk.dotwars.game.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게임 세션(GameSession) — 3일 운영 중 하루(매일 16:00~24:00) 구간을 나타냄.
 *
 * 왜 Game 과 별도 엔티티로 두는가:
 *  - 게임 전체 범위(3일)와 실제 픽셀 칠하기가 허용되는 시간대는 다름.
 *  - 매일 자정에 멈추고 다음 16:00에 재개 → 이 "허용 윈도우" 를 명시적으로 객체화.
 *  - 통계(예: "Day 2 에 가장 많이 칠한 진영") 같은 분석에 dayNumber 컬럼이 그대로 쓰임.
 */
@Entity
@Table(name = "game_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어떤 게임의 세션인지. LAZY: 세션을 조회할 때 게임 정보까지 즉시 필요하지 않은 경우가 많음.
     * optional=false: 부모 게임 없는 세션은 존재 불가.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 1, 2, 3 — 게임 내 몇 일차인지. 통계/표시용으로 자주 쓰임.
    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Builder
    private GameSession(Game game, int dayNumber, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.game = game;
        this.dayNumber = dayNumber;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
