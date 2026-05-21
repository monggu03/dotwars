package com.dongguk.dotwars.user.controller;

import com.dongguk.dotwars.user.dto.SelectDepartmentRequest;
import com.dongguk.dotwars.user.dto.UserMeResponse;
import com.dongguk.dotwars.user.dto.UserPaintsResponse;
import com.dongguk.dotwars.user.service.UserPaintsService;
import com.dongguk.dotwars.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 정보 관련 API.
 *
 * 모든 엔드포인트는 인증 필수 (SecurityConfig 의 anyRequest().authenticated()).
 * JwtAuthenticationFilter 가 토큰을 검증하고 SecurityContext 에 userId(Long) 를 박아주므로
 * 컨트롤러는 @AuthenticationPrincipal 로 받기만 하면 됨.
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserPaintsService userPaintsService;

    /**
     * GET /api/users/me — 본인 + 단과대 + 진영 + 쿨다운.
     *
     * 클라이언트는 응답의 department 가 null 인지 보고 단과대 선택 페이지로 분기.
     */
    @GetMapping
    public UserMeResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getCurrentUser(userId);
    }

    /**
     * POST /api/users/me/department — 단과대 1회 선택.
     *
     * @Valid 가 SelectDepartmentRequest 의 @NotNull 들을 검증 → 실패 시
     * MethodArgumentNotValidException → GlobalExceptionHandler 가 400 으로 매핑.
     */
    @PostMapping("/department")
    public UserMeResponse selectDepartment(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SelectDepartmentRequest request
    ) {
        return userService.selectDepartment(userId, request.departmentId(), request.agreed());
    }

    /**
     * GET /api/users/me/paints — 마이페이지 (본인 페인트 이력).
     *
     * 응답: 누적 칠한 횟수 + 최근 50개 (좌표 + 시각 + alive 플래그).
     */
    @GetMapping("/paints")
    public UserPaintsResponse myPaints(@AuthenticationPrincipal Long userId) {
        return userPaintsService.getMyPaints(userId);
    }
}
