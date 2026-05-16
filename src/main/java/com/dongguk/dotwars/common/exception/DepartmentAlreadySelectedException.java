package com.dongguk.dotwars.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 이미 단과대를 선택한 사용자가 다시 단과대 선택 API 를 호출했을 때.
 *
 * "단과대 변경 불가" 정책은 도메인 객체(User.selectDepartment) 에서 이미 강제 — 거기서
 * IllegalStateException 이 발생함. 컨트롤러 진입 단계에서 미리 잡으면 더 친절한 응답이 되므로
 * 서비스에서 명시적으로 이 예외를 던지는 게 정석. 도메인 강제는 마지막 보루.
 *
 * 409 Conflict — "현재 자원 상태가 요청과 충돌" 의 의미에 정확히 부합.
 */
public class DepartmentAlreadySelectedException extends BusinessException {
    public DepartmentAlreadySelectedException() {
        super(HttpStatus.CONFLICT, "DEPARTMENT_ALREADY_SET",
                "이미 단과대를 선택하셨습니다. 변경할 수 없습니다.");
    }
}
