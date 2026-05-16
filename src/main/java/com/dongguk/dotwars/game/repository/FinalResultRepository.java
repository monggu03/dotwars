package com.dongguk.dotwars.game.repository;

import com.dongguk.dotwars.game.domain.FinalResult;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 최종 결과 영속화. 게임 종료 시 1회 INSERT, 이후 READ 만 발생.
 * 결과 화면 API 에서 game_id 기준으로 조회 — 그 메서드는 통계 단계에서 추가.
 */
public interface FinalResultRepository extends JpaRepository<FinalResult, Long> {
}
