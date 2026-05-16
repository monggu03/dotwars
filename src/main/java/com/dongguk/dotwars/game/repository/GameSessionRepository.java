package com.dongguk.dotwars.game.repository;

import com.dongguk.dotwars.game.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 게임 세션 영속화. 핵심 메서드는 "지금 진행 중인 세션이 있나?".
 *
 * 픽셀 칠하기 요청이 들어올 때마다 이 메서드를 호출해 "현재 활성 윈도우(16:00~24:00)" 인지 검증.
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    /**
     * 현재 시각이 어떤 세션의 [starts_at, ends_at) 구간에 들어가면 그 세션을 반환.
     *
     * 메서드 이름 파생으로도 가능하지만(예:
     *   findFirstByStartsAtLessThanEqualAndEndsAtGreaterThanOrderByDayNumberAsc),
     * 이름이 너무 길어져 가독성 손해 → 짧은 메서드명 + @Query 가 더 명확.
     *
     * 시간 비교 규칙:
     *  - starts_at <= now : 시작 시각 도래
     *  - ends_at  > now   : 종료 전 (정확히 24:00 인 순간은 다음 일과 충돌하지 않도록 미만으로)
     */
    @Query("""
            SELECT s
            FROM GameSession s
            WHERE s.startsAt <= :now AND s.endsAt > :now
            """)
    Optional<GameSession> findCurrentSession(@Param("now") LocalDateTime now);
}
