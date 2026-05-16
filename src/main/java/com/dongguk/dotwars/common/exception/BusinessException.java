package com.dongguk.dotwars.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인 정책 위반 / 비즈니스 규칙 위반을 표현하는 예외의 최상위.
 *
 * 일반 RuntimeException 과 달리:
 *  - HTTP 상태 코드와 application 자체 errorCode 를 함께 들고 다님 → 전역 핸들러가 일관 응답 생성 가능
 *  - "예측 가능한 실패" (= 사용자 요청이 정책상 거부됨) 만 이걸 던짐
 *  - 시스템 장애(DB 끊김, NullPointer 등) 는 그냥 RuntimeException 으로 둠 → 500 처리됨
 *
 * 호출자는 보통 구체 서브클래스만 사용 (DepartmentAlreadySelectedException 등).
 * 이 클래스 자체는 추상은 아니지만 직접 new 하기보다 서브클래스 정의를 권장.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
