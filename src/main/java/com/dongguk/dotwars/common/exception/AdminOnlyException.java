package com.dongguk.dotwars.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 관리자 전용 리소스에 비관리자가 접근할 때.
 * 인증(로그인)은 됐으나 권한(admin kakaoId)이 없는 경우 → 403.
 */
public class AdminOnlyException extends BusinessException {
    public AdminOnlyException() {
        super(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자 전용입니다.");
    }
}
