package com.dongguk.dotwars.game.repository;

import com.dongguk.dotwars.game.domain.PixelHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 픽셀 칠하기 이력. INSERT 가 절대 다수.
 *
 * 통계용 집계 쿼리(GROUP BY faction_id 등) 는 통계 단계에서 추가.
 * 일단 기본 CRUD 만으로 시작 — 인덱스는 엔티티 단에 이미 걸어둠.
 */
public interface PixelHistoryRepository extends JpaRepository<PixelHistory, Long> {
}
