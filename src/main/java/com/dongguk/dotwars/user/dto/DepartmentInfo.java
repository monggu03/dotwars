package com.dongguk.dotwars.user.dto;

/**
 * 응답 안의 단과대 요약 정보. 진영 정보는 FactionInfo 로 분리되어 별도 필드로 동봉됨.
 */
public record DepartmentInfo(Long id, String name) {}
