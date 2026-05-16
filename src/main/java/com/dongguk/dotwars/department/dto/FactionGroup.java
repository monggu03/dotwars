package com.dongguk.dotwars.department.dto;

import java.util.List;

/**
 * 진영 1개 + 그 진영에 속한 단과대 목록.
 * 클라이언트는 colorHex 를 CSS 변수에 박아 진영별 왼쪽 4px 라인을 그림.
 */
public record FactionGroup(
        Long id,
        String name,
        String colorHex,
        List<DepartmentItem> departments
) {}
