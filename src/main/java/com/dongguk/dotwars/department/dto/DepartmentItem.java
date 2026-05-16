package com.dongguk.dotwars.department.dto;

/**
 * 단과대 목록 페이지에서 한 행으로 보여줄 요약 정보.
 * 진영 정보는 상위 FactionGroup 에서 한 번만 표시되므로 여기엔 빠짐.
 */
public record DepartmentItem(Long id, String name) {}
