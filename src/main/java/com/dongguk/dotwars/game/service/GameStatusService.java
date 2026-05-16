package com.dongguk.dotwars.game.service;

import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.domain.GameSession;
import com.dongguk.dotwars.game.domain.GameStatus;
import com.dongguk.dotwars.game.dto.GameStatusResponse;
import com.dongguk.dotwars.game.dto.SessionInfo;
import com.dongguk.dotwars.game.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 게임 라이프사이클 상태 조회 서비스.
 *
 * 진실원천(source of truth):
 *  - status:        Redis (game:status) — 스케줄러가 매초 갱신. 일관된 값.
 *  - 세션 정보:     DB (game_sessions) — 변동 적음, 캐싱 불필요.
 *
 * 왜 Redis 의 status 를 쓰나:
 *  - DB 만 쓰면 매 호출마다 "now 와 세션 시각 비교 + 모든 세션 끝났는지" 같은 로직 반복.
 *  - 스케줄러가 한 곳에서 결정해 Redis 에 박아두면 모든 호출이 동일 값 보장 + 빠름.
 *  - Redis 가 비어있는 비정상 케이스만 DB 로 계산 (fallback).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameStatusService {

    private final RedisTemplate<String, String> redisTemplate;
    private final GameSessionRepository gameSessionRepository;

    public GameStatusResponse getStatus() {
        LocalDateTime now = LocalDateTime.now();

        // Redis 의 status — 스케줄러가 최근에 박은 값. 없으면 DB 로 폴백 계산.
        String status = redisTemplate.opsForValue().get(CanvasRedisKeys.GAME_STATUS);
        Optional<GameSession> current = gameSessionRepository.findCurrentSession(now);
        Optional<GameSession> next = gameSessionRepository.findFirstByStartsAtAfterOrderByStartsAtAsc(now);

        if (status == null) {
            // 폴백: 진행중 → ACTIVE, 미래 세션 있음 → SCHEDULED (게임 시작 전) 또는 FROZEN (게임 중간 휴식),
            // 둘 다 없음 → ENDED. 단순화: 미래 세션 유무로만 결정.
            status = current.isPresent()
                    ? GameStatus.ACTIVE.name()
                    : (next.isPresent() ? GameStatus.FROZEN.name() : GameStatus.ENDED.name());
        }

        SessionInfo currentInfo = current.map(this::toSessionInfo).orElse(null);
        SessionInfo nextInfo = next.map(this::toSessionInfo).orElse(null);

        return new GameStatusResponse(status, currentInfo, nextInfo, Instant.now());
    }

    /**
     * GameSession 의 LocalDateTime (KST, JPA 저장 형식) → Instant (UTC, JSON 응답 형식).
     *
     * 서버 JVM 의 systemDefault 가 Asia/Seoul 이라는 전제 (Docker MySQL 의 TZ 와 일치).
     * KST 9시 → UTC 0시 같은 변환이 여기서 자동으로 일어남.
     */
    private SessionInfo toSessionInfo(GameSession s) {
        ZoneId zone = ZoneId.systemDefault();
        return new SessionInfo(
                s.getDayNumber(),
                s.getStartsAt().atZone(zone).toInstant(),
                s.getEndsAt().atZone(zone).toInstant()
        );
    }
}
