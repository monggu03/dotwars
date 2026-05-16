package com.dongguk.dotwars.user.dto;

/**
 * GET /api/users/me 의 응답.
 *
 * 의도적으로 빠진 항목: nickname.
 *  - design.md 의 절대 금지 #10: "사용자 닉네임 표시" 정책.
 *  - DB 의 nickname 은 "user_<kakaoId>" 자동 생성값이라 사실상 식별자일 뿐 화면에 노출할 가치 없음.
 *
 * department/faction:
 *  - 가입 직후 단과대 미선택 상태에는 둘 다 null. 클라이언트는 null 이면 단과대 선택 페이지로 보낼 것.
 *
 * cooldownRemainingSec:
 *  - 다음 픽셀까지 남은 쿨다운(초). Redis 통합 전엔 항상 0.
 *  - int 면 충분 (최댓값 300 = 5분).
 */
public record UserMeResponse(
        Long userId,
        DepartmentInfo department,
        FactionInfo faction,
        int cooldownRemainingSec
) {}
