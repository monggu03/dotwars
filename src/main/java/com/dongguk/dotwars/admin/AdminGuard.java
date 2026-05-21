package com.dongguk.dotwars.admin;

import com.dongguk.dotwars.common.exception.AdminOnlyException;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자 권한 체크 — 설정된 admin kakaoId 와 현재 사용자의 kakaoId 비교.
 *
 * 왜 kakaoId 기반인가:
 *  - 별도 admin 로그인/비밀번호 없이 기존 카카오 인증 재활용.
 *  - admin kakaoId 가 노출돼도 무방 — 실제 그 카카오 계정으로 로그인해야 하므로 권한 획득 불가.
 *  - app.admin-kakao-id = 0 (기본) 이면 관리자 기능 전체 비활성.
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    @Value("${app.admin-kakao-id:0}")
    private long adminKakaoId;

    private final UserRepository userRepository;

    public boolean isAdmin(Long userId) {
        if (userId == null || adminKakaoId == 0L) return false;
        return userRepository.findById(userId)
                .map(u -> u.getKakaoId() != null && u.getKakaoId() == adminKakaoId)
                .orElse(false);
    }

    /** 비관리자면 403 예외. 관리자 전용 엔드포인트 진입부에서 호출. */
    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new AdminOnlyException();
        }
    }
}
