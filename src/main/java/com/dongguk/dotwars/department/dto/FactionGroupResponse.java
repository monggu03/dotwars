package com.dongguk.dotwars.department.dto;

import java.util.List;

/**
 * GET /api/departments 응답 — 진영별로 묶인 단과대 목록.
 *
 * 응답을 List<FactionGroup> 자체로 두는 대신 한 단계 더 감싼 이유:
 *  - 추후 메타 정보(예: 총 단과대 수, 응답 시각) 를 추가하기 쉽도록.
 *  - 최상위 JSON 이 배열이면 일부 보안 도구가 경고를 띄우는 옛 관례도 있음.
 */
public record FactionGroupResponse(List<FactionGroup> factions) {}
