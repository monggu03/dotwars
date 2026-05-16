package com.dongguk.dotwars.game.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 픽셀 칠하기 이력(PixelHistory) — 누가 언제 어디를 무슨 진영 색으로 칠했는지 한 행씩 적재.
 *
 * 설계 의도:
 *  - 캔버스의 "현재 상태" 는 Redis(canvas:current 해시) 가 진실원천. 이 테이블은 감사 로그.
 *  - 게임 종료 후 통계(진영별 픽셀 점유율 추이, 일자별 활동량 등) 의 원본 데이터.
 *  - 칠하기 1회 = 1 row INSERT → 게임 기간 동안 수만~수십만 행 누적 가능. 인덱스 설계 중요.
 *
 * 왜 User/Faction 객체 대신 raw id(Long) 만 두는가:
 *  - 대량 INSERT 가 핵심 경로. ManyToOne 객체 매핑은 INSERT 시 영속성 컨텍스트 비용 + 추가 SELECT.
 *  - 통계 집계는 GROUP BY faction_id 같은 단순 쿼리로 처리 → 객체 그래프 필요 없음.
 *  - FK 무결성은 application 레벨에서 보장(픽셀 칠하기 서비스가 유효한 user/faction 만 받음).
 */
@Entity
@Table(
        name = "pixel_history",
        // 자주 거는 조건(WHERE user_id, WHERE created_at, WHERE x AND y) 마다 인덱스 1개씩.
        // 인덱스를 너무 많이 걸면 INSERT 비용이 늘지만, 픽셀 이력은 INSERT >> SELECT 비율이라 균형 OK.
        indexes = {
                @Index(name = "idx_user", columnList = "user_id"),
                @Index(name = "idx_created", columnList = "created_at"),
                @Index(name = "idx_position", columnList = "x, y")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PixelHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 좌표는 0..49 범위(50x50). int 면 충분.
    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(name = "faction_id", nullable = false)
    private Long factionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PixelHistory(Long userId, int x, int y, Long factionId) {
        this.userId = userId;
        this.x = x;
        this.y = y;
        this.factionId = factionId;
    }
}
