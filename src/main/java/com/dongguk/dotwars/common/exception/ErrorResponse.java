package com.dongguk.dotwars.common.exception;

import java.util.Map;

/**
 * 모든 에러 응답의 표준 본문.
 *
 * 두 가지 형태:
 *  - 단순 에러:    { "error": "DEPARTMENT_ALREADY_SET", "message": "..." }
 *  - 검증 실패:    { "error": "VALIDATION_FAILED", "message": "...", "fieldErrors": {"departmentId":"필수"} }
 *
 * fieldErrors 는 nullable. 검증 외 에러에선 직렬화에서 제외 (Jackson 기본 동작은 null 도 출력하지만
 * 추후 @JsonInclude(NON_NULL) 적용 시 자연 누락).
 */
public record ErrorResponse(
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    /** 단순 에러용 정적 팩토리. fieldErrors 는 null. */
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null);
    }

    /** 검증 실패용. fieldErrors 채움. */
    public static ErrorResponse validation(String message, Map<String, String> fieldErrors) {
        return new ErrorResponse("VALIDATION_FAILED", message, fieldErrors);
    }
}
