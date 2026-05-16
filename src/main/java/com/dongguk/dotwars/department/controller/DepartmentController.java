package com.dongguk.dotwars.department.controller;

import com.dongguk.dotwars.department.dto.FactionGroupResponse;
import com.dongguk.dotwars.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단과대/진영 조회 API.
 *
 * 공개 라우트 — SecurityConfig 에서 permitAll. 로그인 전 단과대 선택 페이지 렌더링 가능해야 하므로.
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public FactionGroupResponse list() {
        return departmentService.listGroupedByFaction();
    }
}
