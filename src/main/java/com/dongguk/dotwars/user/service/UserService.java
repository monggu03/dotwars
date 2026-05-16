package com.dongguk.dotwars.user.service;

import com.dongguk.dotwars.common.exception.DepartmentAlreadySelectedException;
import com.dongguk.dotwars.common.exception.DepartmentNotFoundException;
import com.dongguk.dotwars.common.exception.UserNotFoundException;
import com.dongguk.dotwars.department.domain.Department;
import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.repository.DepartmentRepository;
import com.dongguk.dotwars.user.domain.User;
import com.dongguk.dotwars.user.dto.DepartmentInfo;
import com.dongguk.dotwars.user.dto.FactionInfo;
import com.dongguk.dotwars.user.dto.UserMeResponse;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 도메인 서비스.
 *
 * 두 가지 책임:
 *  1) 본인 정보 조회 (단과대/진영 포함)
 *  2) 단과대 선택 (1회 한정)
 *
 * @Transactional 클래스 레벨 readOnly = true 후 변경 메서드만 @Transactional 로 덮어쓰기.
 *  - 조회 메서드는 readOnly 트랜잭션으로 Hibernate 가 더티 체킹/스냅샷 비용을 생략 → 미세한 성능 이득
 *  - 변경 메서드는 명시적으로 다시 readOnly=false 트랜잭션 시작
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * 본인 정보 조회 — JWT 필터가 채워준 userId 로 호출됨.
     *
     * 단과대 미선택 사용자도 호출 가능. 그 경우 department/faction 은 null.
     * 클라이언트는 그걸 보고 단과대 선택 페이지로 분기.
     *
     * cooldownRemainingSec 은 일단 0 — Redis 통합 후 갱신.
     */
    public UserMeResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // department LAZY → 이 트랜잭션 안에서 접근해야 함. 외부로 빠져나가기 전에 DTO 로 변환 완료.
        Department department = user.getDepartment();
        if (department == null) {
            return new UserMeResponse(user.getId(), null, null, 0);
        }

        Faction faction = department.getFaction();
        return new UserMeResponse(
                user.getId(),
                new DepartmentInfo(department.getId(), department.getName()),
                new FactionInfo(faction.getId(), faction.getName(), faction.getColorHex()),
                0   // TODO: Redis 통합 시 cooldown:user:{id} TTL 으로 채우기
        );
    }

    /**
     * 단과대 선택 (1회 한정).
     *
     * 검증 순서가 중요:
     *  1) 사용자 존재 여부 (없으면 토큰 위조 의심 → 404)
     *  2) 이미 선택했는지 (409 — 정책 위반은 행위 자체를 막아야 함)
     *  3) 단과대 존재 여부 (404 — 잘못된 입력)
     *  4) 도메인 메서드 호출 (User.selectDepartment 가 최후 방어선)
     *
     * @Transactional 명시 → 클래스 레벨 readOnly=true 를 덮어쓰기. 변경 작업이므로 readOnly=false.
     */
    @Transactional
    public UserMeResponse selectDepartment(Long userId, Long departmentId, boolean agreed) {
        if (!agreed) {
            // 약속 체크박스 미동의 — 비즈니스 예외가 아닌 입력 검증 실패 성격이라 IllegalArgumentException.
            // 전역 핸들러에서 400 으로 매핑.
            throw new IllegalArgumentException("약속에 동의하지 않으면 단과대를 선택할 수 없습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getDepartment() != null) {
            throw new DepartmentAlreadySelectedException();
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        // 도메인 메서드: 이미 set 됐으면 IllegalStateException 발생 — 위에서 이미 막았지만 보루로.
        user.selectDepartment(department);
        log.info("[user] 단과대 선택 user.id={} dept.id={} dept={}",
                userId, departmentId, department.getName());

        // 영속성 컨텍스트의 dirty checking 으로 자동 UPDATE. save 호출 불필요.
        Faction faction = department.getFaction();
        return new UserMeResponse(
                user.getId(),
                new DepartmentInfo(department.getId(), department.getName()),
                new FactionInfo(faction.getId(), faction.getName(), faction.getColorHex()),
                0
        );
    }
}
