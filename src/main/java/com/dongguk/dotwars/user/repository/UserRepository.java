package com.dongguk.dotwars.user.repository;

import com.dongguk.dotwars.user.domain.User;
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
}
