package com.dongguk.dotwars.department.repository;

import com.dongguk.dotwars.department.domain.Faction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 진영 영속화. 시드 후 변경되지 않으므로 사실상 read-only.
 * 시드 데이터 INSERT 시 save() 만 호출하면 충분.
 */
public interface FactionRepository extends JpaRepository<Faction, Long> {
}
