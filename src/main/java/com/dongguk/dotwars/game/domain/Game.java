package com.dongguk.dotwars.game.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게임(Game) — 1회 운영되는 픽셀 점령전 그 자체.
 *
 * 한 학기에 한 번만 운영되더라도 엔티티로 분리한 이유:
 *  - 캔버스 크기/쿨다운 같은 규칙 값이 미래 이벤트에서 달라질 수 있음
 *  - 결과 집계(FinalResult) 와 1:N 관계로 묶일 때 식별 키가 필요
 *  - 동시에 여러 게임을 운영할 일은 없지만 "이번 게임" 을 명시적으로 가리키는 키가 있는 게 안전
 */
@Entity
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // 캔버스 크기(현재 11×17). application.yml 의 game.canvas.width/height 와 시드 데이터에서 일치시킴.
    @Column(name = "canvas_width", nullable = false)
    private int canvasWidth;

    @Column(name = "canvas_height", nullable = false)
    private int canvasHeight;

    // 픽셀 칠한 사용자가 다시 칠할 수 있을 때까지의 대기 시간(초). 현재 운영값 5초.
    // (런타임은 @Value("${game.cooldown-seconds}") 가 권위 — 이 필드는 시드/기록용)
    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds;

    /**
     * 게임 전체 시작/종료 시각. 3일에 걸친 운영 전체 범위.
     * 일일 운영 시간대(16:00~24:00) 는 GameSession 으로 별도 표현 — 운영 시간을 명확히 구분하기 위함.
     */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /**
     * 게임 상태. EnumType.STRING 으로 저장 → DB 에 "ACTIVE" 같은 문자열로 들어감.
     * ORDINAL 저장은 enum 순서가 바뀌면 기존 행의 의미가 깨지므로 절대 사용 금지.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Builder
    private Game(String name, int canvasWidth, int canvasHeight, int cooldownSeconds,
                 LocalDateTime startsAt, LocalDateTime endsAt, GameStatus status) {
        this.name = name;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.cooldownSeconds = cooldownSeconds;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    /**
     * 상태 전환 — 스케줄러가 시각 도래 시 호출.
     * setter 를 열어두지 않는 이유: 임의의 값으로 status 가 바뀌면 라이프사이클 일관성이 깨짐.
     * 명시 메서드로만 변경하면 어디서 상태 전환이 발생하는지 grep 으로 추적 가능.
     */
    public void changeStatus(GameStatus newStatus) {
        this.status = newStatus;
    }
}
