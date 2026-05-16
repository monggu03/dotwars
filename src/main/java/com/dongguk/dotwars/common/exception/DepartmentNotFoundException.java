package com.dongguk.dotwars.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 단과대 선택 요청에서 존재하지 않는 departmentId 가 들어왔을 때.
 * 클라이언트가 잘못된 값을 보낸 케이스 → 404 가 적절 (해당 자원 없음).
 */
public class DepartmentNotFoundException extends BusinessException {
    public DepartmentNotFoundException(Long departmentId) {
        super(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                "단과대를 찾을 수 없습니다 (id=" + departmentId + ")");
    }
}
