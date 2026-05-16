package com.dongguk.dotwars.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/users/me/department 요청.
 *
 * agreed 가 true 인지 검증은 서비스 레이어에서 수행 (record + @AssertTrue 조합은 일부 환경에서
 * accessor 이름 매칭 이슈가 있어 명시적 if 체크가 더 안전). @NotNull 만 어노테이션으로 강제.
 *
 * 약속 체크박스("본인은 ... 본인 소속") 가 클라이언트에서 체크 안 됐으면 false 로 전송되고,
 * 서비스가 IllegalArgumentException 으로 거절 → 400 응답.
 */
public record SelectDepartmentRequest(
        @NotNull(message = "단과대 ID는 필수입니다")
        Long departmentId,

        @NotNull(message = "약속 동의 여부는 필수입니다")
        Boolean agreed
) {}
