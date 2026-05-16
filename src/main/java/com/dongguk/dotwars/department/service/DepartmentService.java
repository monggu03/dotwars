package com.dongguk.dotwars.department.service;

import com.dongguk.dotwars.department.domain.Department;
import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.dto.DepartmentItem;
import com.dongguk.dotwars.department.dto.FactionGroup;
import com.dongguk.dotwars.department.dto.FactionGroupResponse;
import com.dongguk.dotwars.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 단과대/진영 조회 서비스.
 *
 * 현재 단 하나의 메서드: 진영별로 묶인 전체 단과대 목록. 단과대 선택 페이지 렌더링에 사용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * 진영 displayOrder 기준 정렬된 단과대 전체를 한 번 가져와 진영별로 그룹화.
     *
     * - Repository 의 @EntityGraph 가 faction 을 fetch join 으로 함께 로드 → N+1 없음.
     * - LinkedHashMap 으로 진영 등장 순서(= displayOrder 순) 보존.
     * - 같은 진영 안에서는 단과대를 DB 등장 순서로 (시드 시 의도된 순서) 그대로 노출.
     */
    public FactionGroupResponse listGroupedByFaction() {
        List<Department> departments = departmentRepository.findAllByOrderByFactionDisplayOrderAsc();

        // 진영 ID → FactionGroup 매핑을 순서 보존하며 누적.
        Map<Long, FactionGroupBuilder> buckets = new LinkedHashMap<>();
        for (Department dept : departments) {
            Faction faction = dept.getFaction();
            FactionGroupBuilder bucket = buckets.computeIfAbsent(
                    faction.getId(),
                    id -> new FactionGroupBuilder(faction)
            );
            bucket.add(new DepartmentItem(dept.getId(), dept.getName()));
        }

        List<FactionGroup> result = buckets.values().stream()
                .map(FactionGroupBuilder::build)
                .toList();
        return new FactionGroupResponse(result);
    }

    /**
     * 임시 버퍼 — 진영 정보 + 단과대 누적용. record 는 mutable list 누적이 어려워 작은 클래스로.
     * 클래스 외부에서 쓸 일 없으므로 private static.
     */
    private static final class FactionGroupBuilder {
        private final Faction faction;
        private final List<DepartmentItem> items = new java.util.ArrayList<>();

        FactionGroupBuilder(Faction faction) { this.faction = faction; }

        void add(DepartmentItem item) { items.add(item); }

        FactionGroup build() {
            return new FactionGroup(
                    faction.getId(),
                    faction.getName(),
                    faction.getColorHex(),
                    List.copyOf(items)   // 불변 사본으로 노출
            );
        }
    }
}
