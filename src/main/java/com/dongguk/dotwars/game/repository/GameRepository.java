package com.dongguk.dotwars.game.repository;

import com.dongguk.dotwars.game.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게임 영속화.
 *
 * 우리 도메인에는 동시에 운영 중인 게임이 1개만 존재한다는 비즈니스 약속이 있음.
 * → "현재 게임" 을 찾는 메서드는 추후 GameService 가 "id=1 의 게임" 또는 "가장 최신" 등으로
 *    명시적으로 정의. Repository 자체는 일반 CRUD 만.
 */
public interface GameRepository extends JpaRepository<Game, Long> {
}
