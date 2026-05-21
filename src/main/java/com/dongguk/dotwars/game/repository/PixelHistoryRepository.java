package com.dongguk.dotwars.game.repository;

import com.dongguk.dotwars.game.domain.PixelHistory;
import com.dongguk.dotwars.game.repository.projection.CellCountProjection;
import com.dongguk.dotwars.game.repository.projection.HourActivityProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 픽셀 칠하기 이력. INSERT 가 절대 다수.
 *
 * 인덱스는 엔티티 단에 이미 걸어둠 (idx_user, idx_created, idx_position).
 */
public interface PixelHistoryRepository extends JpaRepository<PixelHistory, Long> {

    /** 마이페이지 — 본인이 칠한 누적 횟수 */
    long countByUserId(Long userId);

    /** 마이페이지 — 본인이 칠한 최근 N개 (createdAt 내림차순) */
    List<PixelHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── 관리자 대시보드 집계 ──────────────────────────────────────

    /** 최근 1분(부하 지표) 등 특정 시각 이후 페인트 수 */
    long countByCreatedAtAfter(LocalDateTime since);

    /** 실시간 페인트 로그 — 최근 20개 */
    List<PixelHistory> findTop20ByOrderByCreatedAtDesc();

    /** 칸별 누적 페인트 수 (격전지 랭킹 + 히트맵). count 내림차순. idx_position 활용. */
    @Query(value = "SELECT x AS x, y AS y, COUNT(*) AS cnt "
            + "FROM pixel_history GROUP BY x, y ORDER BY cnt DESC", nativeQuery = true)
    List<CellCountProjection> findCellCounts();

    /** 시간대별 활동량 — 'MM-DD HH시' 버킷. 시간 순. */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%m-%d %H시') AS bucket, COUNT(*) AS cnt "
            + "FROM pixel_history GROUP BY bucket ORDER BY MIN(created_at)", nativeQuery = true)
    List<HourActivityProjection> findHourlyActivity();
}
