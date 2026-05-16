package com.dongguk.dotwars.department.repository;

import com.dongguk.dotwars.department.domain.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 단과대 영속화.
 *
 * 단과대 목록 API (GET /api/departments) 는 화면에 진영명/색상까지 함께 보여줘야 하므로
 * 단과대만 로드하면 화면 렌더링 중 N+1 발생. → fetch join 으로 한 번에 가져옴.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * 메서드 이름 파생으로 다음 쿼리가 생성됨:
     *   SELECT d FROM Department d ORDER BY d.faction.displayOrder ASC
     *
     * @EntityGraph(attributePaths = "faction"):
     *   기본은 LAZY 라서 fetch 1번 + faction 접근마다 추가 SELECT(N+1) 발생.
     *   EntityGraph 가 붙으면 JOIN 으로 한 번에 가져옴.
     *   같은 효과를 @Query 의 "JOIN FETCH" 로도 낼 수 있지만, 메서드 이름 파생을 살리고 싶을 때 이 방식이 유리.
     */
    @EntityGraph(attributePaths = "faction")
    List<Department> findAllByOrderByFactionDisplayOrderAsc();
}
