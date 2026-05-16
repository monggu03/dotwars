package com.dongguk.dotwars.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 토큰 검증은 통과했는데 그 userId 로 DB 조회 시 행이 없을 때.
 * 정상 흐름이면 발생 불가 — DB 삭제/오염 같은 비정상 상황 신호.
 */
public class UserNotFoundException extends BusinessException {
    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다 (id=" + userId + ")");
    }
}
