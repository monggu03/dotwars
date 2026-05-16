package com.dongguk.dotwars.department.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진영(Faction) — 5개 단과대 그룹을 묶는 색깔별 팀.
 *
 * 도메인적 의미: "점령전의 참가 팀". 단과대(Department)들이 이 진영에 소속됨.
 *
 * 시드 데이터로 5개(인문/사회/자연/공학/예술)만 한 번 INSERT 되고 그 이후엔 변경되지 않음.
 * → application 코드에서 진영을 새로 만드는 API는 없음. 읽기 전용에 가깝게 다뤄짐.
 */
@Entity
@Table(name = "factions")
@Getter
// JPA 는 리플렉션으로 기본 생성자를 호출해 엔티티를 만듦. public 으로 열어두면 application 코드에서
// new Faction() 으로 빈 인스턴스를 만들 수 있어 위험 → protected 로 좁혀 외부에서 호출 불가하게.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faction {

    @Id
    // IDENTITY: MySQL 의 AUTO_INCREMENT 사용. INSERT 시 DB 가 PK 값을 매겨 반환.
    // SEQUENCE 는 MySQL 미지원, TABLE 은 별도 테이블 부담이 있어 IDENTITY 가 정석.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique=true: 진영 이름은 사람이 읽는 식별자라 중복 금지(예: "인문진영" 1개만).
    @Column(nullable = false, unique = true, length = 30)
    private String name;

    // "#RRGGBB" 7자 고정. 캔버스 클라이언트에 그대로 내려보내면 CSS color 로 즉시 사용 가능.
    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    // 진영을 화면에 나열할 때의 순서(인문→사회→자연→공학→예술). 단순 정렬 키.
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder
    private Faction(String name, String colorHex, int displayOrder) {
        this.name = name;
        this.colorHex = colorHex;
        this.displayOrder = displayOrder;
    }
}
