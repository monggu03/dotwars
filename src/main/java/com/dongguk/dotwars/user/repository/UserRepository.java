package com.dongguk.dotwars.user.repository;

import com.dongguk.dotwars.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 영속화 — JpaRepository 가 save/findById/delete 등 기본 CRUD 제공.
 *
 * 우리만의 메서드는 카카오 ID 로 찾는 한 가지. 카카오 로그인 콜백에서 매번 호출됨:
 *   "이 카카오 회원번호의 사용자가 이미 있나? 없으면 만들고, 있으면 그 행을 사용"
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Spring Data JPA 의 메서드 이름 파생(method name derivation):
     *   findBy<필드명> → SELECT * FROM users WHERE kakao_id = ?
     * 별도 @Query 없이 인터페이스 메서드 이름만으로 쿼리가 자동 생성됨.
     *
     * Optional 반환: 카카오 ID 가 없을 수도 있는(신규 가입) 경우를 호출자가 명시적으로 처리하도록 강제.
     */
    Optional<User> findByKakaoId(Long kakaoId);

    /**
     * 사용자 + 단과대 + 진영을 한 쿼리로 fetch join.
     *
     * 왜 필요한가:
     *  - User.department 는 LAZY → 트랜잭션 밖에서 user.getDepartment() 호출 시 세션 없어 폭발.
     *  - 픽셀 칠하기(CanvasService.paintPixel) 는 Redis 위주라 @Transactional 을 쓰면 DB 트랜잭션이
     *    Redis I/O 동안 계속 열려있음 → 자원 낭비.
     *  - fetch join 으로 1쿼리에 다 가져오면 트랜잭션 박을 필요 없이 LAZY 회피.
     *
     * @EntityGraph 의 attributePaths:
     *   - "department"            : User → Department 까지 join
     *   - "department.faction"    : Department → Faction 까지 한 단계 더 join
     *   결과 SQL: LEFT JOIN departments LEFT JOIN factions, 한 round-trip.
     */
    @EntityGraph(attributePaths = {"department", "department.faction"})
    Optional<User> findWithDepartmentAndFactionById(Long id);
}
