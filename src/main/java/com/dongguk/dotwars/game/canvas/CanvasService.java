package com.dongguk.dotwars.game.canvas;

import com.dongguk.dotwars.common.exception.CooldownActiveException;
import com.dongguk.dotwars.common.exception.GameNotActiveException;
import com.dongguk.dotwars.common.exception.UserNotFoundException;
import com.dongguk.dotwars.game.websocket.CanvasBroadcastService;
import com.dongguk.dotwars.user.domain.User;
import com.dongguk.dotwars.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 캔버스 도메인 서비스 — 픽셀 칠하기 + 캔버스/카운트 조회.
 *
 * 진실원천(source of truth) 분리:
 *   - Redis: 캔버스 현재 상태, 진영 카운트, 쿨다운, 게임 상태 (실시간 읽기/쓰기가 핵심)
 *   - MySQL: 사용자/단과대/진영(시드) + 픽셀 이력(감사 로그)
 *
 * 왜 Redis 인가:
 *   - 단일 명령(SET/HSET/HINCRBY) 모두 원자적 → 동시성 처리 매우 단순
 *   - In-memory → 픽셀 칠하기/조회 응답이 1ms 이하
 *   - MySQL 로 같은 일 하면 UPDATE 충돌 시 InnoDB 락 → 동시 100명 칠하면 직렬화돼 느려짐
 *
 * Redis "원자성" 이라는 단어가 다소 광범위 — 정확히는:
 *   - 단일 명령은 다른 명령과 인터리브 안 됨 (Redis 가 single-threaded)
 *   - 따라서 SETNX 1번, HINCRBY 1번 같은 단일 호출은 race condition 면역
 *   - 여러 명령을 묶어야 하는 경우엔 MULTI/EXEC, Lua 스크립트 필요 (우리는 분리 안 해도 충분)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CanvasService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final PixelHistoryAsyncService historyAsyncService;
    private final CanvasBroadcastService broadcastService;

    // 쿨다운 초. application.yml 의 game.cooldown-seconds 와 일치 (운영 중 yml 만 바꿔도 반영)
    // 기본값은 yml 누락 시 fallback — 실제 운영 값(5초)과 일치시켜 옛 300초로 잘못 부팅되는 함정 방지.
    @Value("${game.cooldown-seconds:5}")
    private int cooldownSeconds;

    // 캔버스 크기 — 응답 생성/검증에 사용. 기본값도 실제 11×17 과 일치시킴.
    @Value("${game.canvas.width:11}")
    private int canvasWidth;

    @Value("${game.canvas.height:17}")
    private int canvasHeight;

    // ─────────────────────────────────────────────────────────────────
    //  핵심: 픽셀 칠하기
    // ─────────────────────────────────────────────────────────────────

    /**
     * 픽셀 1개 칠하기 — 동시성 처리의 핵심 로직.
     *
     * 흐름 (각 단계 의도와 원자성 보장 이유 참고):
     *   1) 게임 상태 검사 (game:status == ACTIVE)
     *   2) 쿨다운 SET NX EX  — 사용자별 5분 쿨다운 원자적 획득 시도
     *   3) 사용자/진영 조회   — DB 한 번 (캐싱은 다음 단계)
     *   4) 이전 픽셀 색 조회  — HGET, 진영 카운트 증감 결정용
     *   5) 캔버스 HSET        — 새 색으로 덮어쓰기
     *   6) 진영 카운트 HINCRBY — 이전 진영 -1, 새 진영 +1 (원자적)
     *   7) 비동기 히스토리 INSERT
     *
     * 트랜잭션 미사용 이유:
     *   - Redis 명령은 각각 원자적이라 트랜잭션 불필요
     *   - DB INSERT 는 비동기 별도 트랜잭션
     *   - 결합한 트랜잭션이 굳이 필요하지 않은 시나리오
     *   - 단, 2번 통과 후 3~6번 실패 시 쿨다운만 박혀서 사용자가 5분 잠김 → catch 로 cleanup
     */
    public PixelPaintResult paintPixel(Long userId, int x, int y) {
        validateCoordinates(x, y);

        ValueOperations<String, String> values = redisTemplate.opsForValue();
        HashOperations<String, String, String> hashes = redisTemplate.opsForHash();

        // ── 1) 게임 상태 검사 ────────────────────────────────────────
        // 단일 GET — 그 사이에 상태가 바뀔 수 있지만, 그건 "스케줄러가 freeze 한 직후 1픽셀 더 들어옴"
        // 수준의 미세한 경계 케이스라 허용. 엄격하게 막으려면 Lua 스크립트로 원자적 검사+SET 필요.
        String status = values.get(CanvasRedisKeys.GAME_STATUS);
        if (!"ACTIVE".equals(status)) {
            throw new GameNotActiveException(status == null ? "UNKNOWN" : status);
        }

        // ── 2) 쿨다운: SET NX EX (원자적) ────────────────────────────
        // setIfAbsent = Redis 의 "SET key value NX EX seconds" 명령에 매핑.
        //   - NX (Not eXists): 키가 없을 때만 set, 있으면 실패하고 false 반환
        //   - EX seconds: 동시에 TTL 부여, 별도 EXPIRE 명령 없이 한 번에 끝남
        //
        // 왜 SET NX 가 race condition 면역인가:
        //   - 동시에 10명이 같은 키로 시도해도 Redis 는 single-threaded → 10개 명령을 순차 처리
        //   - 첫 번째 명령만 키를 만들고 true 반환, 나머지 9개는 모두 false (이미 있음)
        //   - "GET 후 없으면 SET" 처럼 두 단계로 나누면 그 사이에 다른 요청이 끼어들어 둘 다 통과 가능
        //   - SET NX 는 한 명령이라 그 틈이 없음. 이게 Redis "원자성" 의 핵심.
        String cooldownKey = CanvasRedisKeys.cooldownKey(userId);
        Boolean acquired = values.setIfAbsent(
                cooldownKey,
                "1",
                Duration.ofSeconds(cooldownSeconds)
        );

        if (Boolean.FALSE.equals(acquired)) {
            // 이미 쿨다운 중 → 남은 시간 조회 후 429 응답으로 클라이언트에 안내
            Long remaining = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            int remainingSec = (remaining == null || remaining < 0) ? cooldownSeconds : remaining.intValue();
            throw new CooldownActiveException(remainingSec);
        }

        try {
            // ── 3) 사용자 + 진영 조회 ──────────────────────────────
            // fetch join 으로 user + department + faction 을 한 쿼리에 가져옴 → LAZY 회피.
            // 일반 findById 를 쓰면 트랜잭션 밖에서 user.getDepartment() 호출 시
            // LazyInitializationException 발생. 그래서 명시적 fetch 쿼리 사용.
            User user = userRepository.findWithDepartmentAndFactionById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            if (user.getDepartment() == null) {
                // 단과대 미선택 사용자는 픽셀 칠 권리 없음.
                // IllegalArgumentException 으로 던져 GlobalExceptionHandler 가 400 으로 응답하게 함
                // (IllegalStateException 은 핸들러가 없어 500 + ERROR 로그로 떨어지는 함정).
                throw new IllegalArgumentException("단과대 선택이 필요합니다.");
            }
            Long factionId = user.getDepartment().getFaction().getId();

            // ── 4) 이전 픽셀 색 조회 ───────────────────────────────
            // 진영 카운트 증감 결정에 필요. 흰색(미칠해짐)이면 새 진영 +1 만,
            // 다른 진영이면 그 진영 -1 + 새 진영 +1, 같은 진영이면 변화 없음.
            String pixelField = CanvasRedisKeys.pixelField(x, y);
            String prevValue = hashes.get(CanvasRedisKeys.CANVAS_CURRENT, pixelField);
            Long prevFactionId = prevValue == null ? null : Long.parseLong(prevValue);

            // ── 5) 캔버스 업데이트 (HSET, 원자적) ────────────────────
            // HSET 도 단일 명령이라 원자적. 동시에 같은 좌표를 두 명이 칠해도 둘 다 성공하고
            // 마지막 쓰기가 이김 (last-write-wins). 쿨다운으로 사용자 단위 동시 호출은 이미 차단됨.
            hashes.put(CanvasRedisKeys.CANVAS_CURRENT, pixelField, String.valueOf(factionId));

            // ── 6) 진영 카운트 증감 (HINCRBY, 원자적) ───────────────
            // HINCRBY 는 "field 의 값을 N 증감" 을 단일 원자 연산으로.
            // 동시 호출 시 race condition 없음 — INCR/DECR 가 Redis 의 대표적 atomic 패턴.
            if (prevFactionId == null) {
                // 흰색 → 우리 진영: +1
                hashes.increment(CanvasRedisKeys.FACTION_COUNT, factionId.toString(), 1L);
            } else if (!prevFactionId.equals(factionId)) {
                // 다른 진영 → 우리 진영: 이전 진영 -1, 우리 진영 +1
                hashes.increment(CanvasRedisKeys.FACTION_COUNT, prevFactionId.toString(), -1L);
                hashes.increment(CanvasRedisKeys.FACTION_COUNT, factionId.toString(), 1L);
            }
            // 같은 진영 색 덮어쓰기는 카운트 변화 없음 → 아무 것도 안 함.

            // ── 7) 비동기 픽셀 이력 저장 ───────────────────────────
            // 별도 스레드에서 INSERT — 응답 지연 0. 큐가 가득 차 제출이 거부되거나(TaskRejectedException)
            // 비동기 실패해도 try/catch 로 삼켜 "이력만 누락, 메인 흐름은 무사" 를 실제로 보장.
            // (감싸지 않으면 이미 Redis 반영된 픽셀의 쿨다운이 catch 에서 롤백돼 500 → 중복 페인트 위험)
            try {
                historyAsyncService.save(userId, x, y, factionId);
            } catch (RuntimeException ex) {
                log.warn("[paint] 픽셀 이력 비동기 저장 제출 실패 (이력만 누락) user={} ({},{}): {}", userId, x, y, ex.toString());
            }

            // ── 8) WebSocket 브로드캐스트 ─────────────────────────
            // 모든 구독자(/topic/canvas) 에게 즉시 알림. SimpMessagingTemplate.convertAndSend 는
            // 메모리 큐 push 라 빠름 → 동기 호출이어도 응답 지연 미미.
            // 부하 테스트에서 병목이면 그 때 비동기 도입 검토.
            broadcastService.broadcastPixelPainted(x, y, factionId);

            Instant cooldownEndsAt = Instant.now().plusSeconds(cooldownSeconds);
            log.debug("[paint] user={} ({},{}) {} -> {} ", userId, x, y, prevFactionId, factionId);
            return new PixelPaintResult(x, y, factionId, prevFactionId, cooldownEndsAt);

        } catch (RuntimeException e) {
            // 2번에서 쿨다운만 박혔는데 3~6번이 실패하면 사용자가 부당하게 5분 잠김.
            // 정합성 회복: 쿨다운 키 즉시 삭제 후 예외 재throw → 사용자가 다시 시도 가능.
            redisTemplate.delete(cooldownKey);
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  조회
    // ─────────────────────────────────────────────────────────────────

    /**
     * 현재 캔버스 전체 상태 — 11×17 그리드.
     * 0 = 흰색(미칠해짐), 1~5 = factionId.
     *
     * HGETALL 은 키-값 전체를 1 round-trip 으로 받음. 11×17=187 칸이라 페이로드 작음.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getCanvasRaw() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(CanvasRedisKeys.CANVAS_CURRENT);
        // RedisTemplate<String, String> 이지만 HashOperations 반환 타입이 Object 라 변환.
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>(entries.size());
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            out.put((String) e.getKey(), (String) e.getValue());
        }
        return out;
    }

    public int[][] getCurrentCanvas() {
        Map<String, String> raw = getCanvasRaw();
        int[][] pixels = new int[canvasHeight][canvasWidth];  // 기본값 0 = 흰색
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String[] xy = e.getKey().split(",");
            int x = Integer.parseInt(xy[0]);
            int y = Integer.parseInt(xy[1]);
            // 캔버스 응답은 행(row)이 y, 열(column)이 x — 클라이언트가 pixels[y][x] 로 그리기 쉽도록
            pixels[y][x] = Integer.parseInt(e.getValue());
        }
        return pixels;
    }

    /** 진영별 픽셀 카운트 — factionId → count. */
    public Map<Long, Long> getFactionCounts() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(CanvasRedisKeys.FACTION_COUNT);
        Map<Long, Long> out = new HashMap<>(raw.size());
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            out.put(Long.parseLong((String) e.getKey()), Long.parseLong((String) e.getValue()));
        }
        return out;
    }

    /**
     * 쿨다운 남은 시간(초). 없거나 만료됐으면 0.
     */
    public int getCooldownRemainingSec(Long userId) {
        Long ttl = redisTemplate.getExpire(CanvasRedisKeys.cooldownKey(userId), TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return 0;
        }
        return ttl.intValue();
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    private void validateCoordinates(int x, int y) {
        if (x < 0 || x >= canvasWidth || y < 0 || y >= canvasHeight) {
            // @Valid 에서 이미 막히지만 서비스 진입 직접 호출 대비 보루.
            throw new IllegalArgumentException(
                    "좌표가 캔버스 범위를 벗어났습니다. x=" + x + " y=" + y);
        }
    }
}
