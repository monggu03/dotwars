package com.dongguk.dotwars.admin;

import com.dongguk.dotwars.admin.dto.AdminStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 전용 API.
 *
 * 인증(로그인)은 SecurityConfig 의 anyRequest().authenticated() 가 보장.
 * 추가로 AdminGuard 가 kakaoId 일치(=관리자)까지 확인 → 비관리자는 403.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminGuard adminGuard;
    private final AdminStatsService adminStatsService;

    /** 대시보드 데이터 일괄 조회. */
    @GetMapping("/stats")
    public AdminStatsResponse stats(@AuthenticationPrincipal Long userId) {
        adminGuard.requireAdmin(userId);
        return adminStatsService.getStats();
    }

    /** 대시보드 페이지 로드 시 권한 확인용 (admin 아니면 페이지가 리다이렉트). */
    @GetMapping("/check")
    public Map<String, Boolean> check(@AuthenticationPrincipal Long userId) {
        return Map.of("admin", adminGuard.isAdmin(userId));
    }
}
