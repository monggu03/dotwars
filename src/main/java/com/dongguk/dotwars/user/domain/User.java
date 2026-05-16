package com.dongguk.dotwars.user.domain;

import com.dongguk.dotwars.department.domain.Department;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자(User) — 카카오 로그인으로 생성됨.
 *
 * 정책:
 *  - 카카오 ID 가 사용자를 식별하는 유일 키. 닉네임은 화면 표시용으로만 받음.
 *  - 첫 로그인 시 department = null 로 생성됨 → /api/users/me/department 호출로 1회 선택.
 *  - 단과대를 한 번 선택하면 절대 변경 불가 (정책 차원의 제약 → 도메인 메서드에 강제).
 */
@Entity
// "user" 는 PostgreSQL/MySQL 예약어 충돌 가능성이 있어 관례적으로 "users" 로 둠.
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 카카오 회원번호. unique 로 강제해 중복 로그인 시 항상 같은 User 행에 매핑됨.
     * Long 인 이유: 카카오 ID 는 9~10자리 정수형으로 내려오므로 String 보다 Long 이 자연.
     */
    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    // 카카오에서 받은 닉네임. 길이 제한은 카카오 정책상 안전한 50자.
    @Column(nullable = false, length = 50)
    private String nickname;

    /**
     * 사용자가 속한 단과대.
     * nullable = true: 가입 직후엔 단과대 미선택 상태로 존재.
     * LAZY: 게임 플레이 중엔 user.department 를 자주 안 봄. 픽셀 칠하기 시점에 한 번 fetch.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // 가입 시각 — Hibernate 가 자동으로 현재 시각을 채워줌. JPA 표준의 @PrePersist 보다 짧음.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private User(Long kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
    }

    /**
     * 카카오 정보로부터 User 생성 — application 코드에서 사용하는 유일한 생성 경로.
     * 빌더를 private 으로 닫고 이 정적 팩토리만 노출하는 이유:
     *   - "어떻게 만들어지는가" 를 도메인 자체가 통제 → 일관성/실수 방지
     *   - 빌더에 더 많은 인자를 노출하면 외부에서 createdAt 등을 임의로 채울 수 있어 위험
     */
    public static User from(Long kakaoId, String nickname) {
        return User.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .build();
    }

    /**
     * 단과대 선택 (1회 한정).
     * 이미 선택한 사용자가 이 메서드를 다시 호출하면 예외 → 정책 위반을 도메인이 직접 방어.
     * (서비스 레이어에서만 막으면 누군가 다른 경로에서 우회할 위험. 도메인에 박는 게 안전.)
     */
    public void selectDepartment(Department department) {
        if (this.department != null) {
            throw new IllegalStateException("단과대는 한 번만 선택할 수 있습니다.");
        }
        this.department = department;
    }
}
