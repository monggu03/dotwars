package com.dongguk.dotwars.user.service;

import com.dongguk.dotwars.common.exception.UserNotFoundException;
import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.domain.PixelHistory;
import com.dongguk.dotwars.game.repository.PixelHistoryRepository;
import com.dongguk.dotwars.user.domain.User;
import com.dongguk.dotwars.user.dto.PaintInfo;
import com.dongguk.dotwars.user.dto.UserPaintsResponse;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 마이페이지 — 본인이 칠한 픽셀 이력 조회.
 *
 * 흐름:
 *  1. 본인 진영 식별 (User → Department → Faction)
 *  2. pixel_history 에서 최근 LIMIT 개 SELECT (createdAt DESC, idx_user 인덱스 활용)
 *  3. 그 좌표들의 현재 Redis 상태를 한 번에 HMGET (배치)
 *  4. 각 row 에 alive 플래그 부착 — 현재 셀의 factionId 가 내 진영과 같은가
 *
 * alive 의미:
 *  - true  → 그 셀이 여전히 내 진영 색
 *  - false → 다른 진영이 덮었거나 / 같은 진영의 다른 사람이 덮었거나
 *  *같은 진영 동료의 덮음도 false 로 잡힘 — 시각상 색은 같지만 "내가 칠한 그 픽셀" 은 사라진 상태.
 */
@Service
@RequiredArgsConstructor
public class UserPaintsService {

    /** 한 번에 보여줄 최근 페인트 개수 — UI 모달이 길어지지 않도록 제한 */
    private static final int LIMIT = 50;

    private final UserRepository userRepository;
    private final PixelHistoryRepository pixelHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional(readOnly = true)
    public UserPaintsResponse getMyPaints(Long userId) {
        User user = userRepository.findWithDepartmentAndFactionById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Faction faction = (user.getDepartment() != null) ? user.getDepartment().getFaction() : null;

        // 단과대 미선택 사용자 — 페인트도 없음, 진영도 null
        if (faction == null) {
            return new UserPaintsResponse(0L, List.of(), null, null);
        }

        long total = pixelHistoryRepository.countByUserId(userId);

        List<PixelHistory> recent = pixelHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, LIMIT)
        );

        if (recent.isEmpty()) {
            return new UserPaintsResponse(total, List.of(), faction.getName(), faction.getColorHex());
        }

        // 각 페인트 좌표의 현재 셀 상태를 배치 조회. 셀 1개당 별도 GET 호출 X.
        List<String> fields = recent.stream()
                .map(p -> CanvasRedisKeys.pixelField(p.getX(), p.getY()))
                .toList();

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Object> rawValues = redisTemplate.opsForHash()
                .multiGet(CanvasRedisKeys.CANVAS_CURRENT, (List) fields);
        // 결과 길이 = fields 길이. 키 없으면 해당 인덱스에 null.
        List<Object> currentValues = (rawValues != null) ? rawValues : Collections.emptyList();

        String myFactionId = String.valueOf(faction.getId());

        List<PaintInfo> paints = IntStream.range(0, recent.size())
                .mapToObj(i -> {
                    PixelHistory p = recent.get(i);
                    Object current = (i < currentValues.size()) ? currentValues.get(i) : null;
                    boolean alive = (current != null) && myFactionId.equals(current.toString());
                    return new PaintInfo(p.getX(), p.getY(), p.getCreatedAt(), alive);
                })
                .toList();

        return new UserPaintsResponse(total, paints, faction.getName(), faction.getColorHex());
    }
}
