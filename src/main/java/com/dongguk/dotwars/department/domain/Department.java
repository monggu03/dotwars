package com.dongguk.dotwars.department.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단과대(Department) — 사용자가 소속될 최소 단위. 12개 단과대가 시드로 들어감.
 *
 * 각 단과대는 1개 진영(Faction)에 소속.
 * 사용자는 로그인 후 1회 단과대를 선택하면 변경 불가 → 결과적으로 진영도 1회 선택 후 고정.
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 단과대 이름은 사람이 식별하는 유일한 라벨이라 중복 금지 (예: "공과대학" 1개).
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * 단과대 → 진영 매핑.
     * fetch = LAZY: 단과대를 조회할 때 진영 정보가 곧바로 필요한 경우가 적음.
     *   - 기본값 EAGER 이면 SELECT department 가 항상 SELECT faction 까지 동반 → N+1 비용.
     *   - 정말 필요할 때만 fetch join 으로 한 번에 가져오는 방식이 정석.
     * nullable = false: 진영 없는 단과대는 비즈니스적으로 존재할 수 없음.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faction_id", nullable = false)
    private Faction faction;

    @Builder
    private Department(String name, Faction faction) {
        this.name = name;
        this.faction = faction;
    }
}
