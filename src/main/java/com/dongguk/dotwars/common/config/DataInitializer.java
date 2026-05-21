package com.dongguk.dotwars.common.config;

import com.dongguk.dotwars.department.domain.Department;
import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.repository.DepartmentRepository;
import com.dongguk.dotwars.department.repository.FactionRepository;
import com.dongguk.dotwars.game.canvas.CanvasRedisKeys;
import com.dongguk.dotwars.game.domain.Game;
import com.dongguk.dotwars.game.domain.GameSession;
import com.dongguk.dotwars.game.domain.GameStatus;
import com.dongguk.dotwars.game.repository.GameRepository;
import com.dongguk.dotwars.game.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 앱 시작 시 1회 시드 데이터를 채워 넣음.
 *
 * 시드 항목:
 *  - 진영 5개 (인문/사회/자연/공학/예술)
 *  - 단과대 12개 (각 진영에 매핑)
 *  - 게임 1개 (오늘 16:00 ~ Day3 24:00)
 *  - 게임 세션 3개 (Day 1/2/3, 매일 16:00~24:00)
 *
 * 중복 INSERT 방지: 이미 진영이 1개라도 있으면 전체 시드 스킵. ddl-auto: update 가
 * 테이블만 만들어주는 상황에서, 행이 이미 있으면 다시 넣지 않는 멱등성을 보장.
 *
 * @Transactional 이유:
 *  - 시드 도중 어딘가 실패하면 부분적으로 들어간 데이터가 남으면 안 됨 (예: 진영만 있고 단과대는 없음).
 *  - 전체를 한 트랜잭션으로 묶어 all-or-nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final FactionRepository factionRepository;
    private final DepartmentRepository departmentRepository;
    private final GameRepository gameRepository;
    private final GameSessionRepository gameSessionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // DB 시드와 Redis 시드는 독립 — 트랜잭션 분리. DB 만 @Transactional 메서드로 묶음.
        seedDatabaseIfEmpty();
        seedRedisGameStatusIfEmpty();
    }

    /** DB 시드 — 진영/단과대/게임/세션을 한 트랜잭션으로 일괄 처리. */
    @Transactional
    void seedDatabaseIfEmpty() {
        if (factionRepository.count() > 0) {
            log.info("[시드] 이미 존재 → 스킵");
            return;
        }
        log.info("[시드] INSERT 시작");

        Map<String, Faction> factionsByName = seedFactions();
        seedDepartments(factionsByName);
        Game game = seedGame();
        seedSessions(game);

        log.info("[시드] INSERT 완료 — factions={}, departments={}, games={}, sessions={}",
                factionRepository.count(),
                departmentRepository.count(),
                gameRepository.count(),
                gameSessionRepository.count());
    }

    /**
     * Redis 의 game:status 가 없으면 ACTIVE 로 시드.
     *
     * 개발 편의: 부팅 직후 픽셀 칠하기를 바로 테스트하려면 ACTIVE 상태가 필요.
     * setIfAbsent 사용 — 운영에서는 스케줄러가 16:00 도래 시 ACTIVE 로 전환하지만,
     * 개발 환경에서 매번 재부팅 후 수동으로 SET 할 필요 없도록 idempotent 하게.
     *
     * 운영 단계에서는 이 메서드를 비활성화하거나, 시간 기반 스케줄러로 교체 예정.
     */
    void seedRedisGameStatusIfEmpty() {
        Boolean set = redisTemplate.opsForValue().setIfAbsent(
                CanvasRedisKeys.GAME_STATUS,
                GameStatus.ACTIVE.name()
        );
        if (Boolean.TRUE.equals(set)) {
            log.info("[시드] Redis game:status = ACTIVE (신규 설정)");
        } else {
            String current = redisTemplate.opsForValue().get(CanvasRedisKeys.GAME_STATUS);
            log.info("[시드] Redis game:status 이미 존재 = {} → 유지", current);
        }
    }

    /**
     * 진영 5개를 INSERT 하고 이름→객체 매핑을 반환.
     * 단과대 INSERT 단계에서 진영 객체 참조가 필요해서 매핑 형태로 넘겨줌.
     */
    private Map<String, Faction> seedFactions() {
        List<Faction> factions = List.of(
                // displayOrder 는 화면에 나열되는 순서. 인문→사회→자연→공학→예술 순.
                // 2026-05-19: Game Vibrant 팔레트 — HSL 명도+15, 채도 보강. 색조(hue) 는 동일.
                Faction.builder().name("인문진영").colorHex("#FFA040").displayOrder(1).build(),
                Faction.builder().name("사회진영").colorHex("#F04545").displayOrder(2).build(),
                Faction.builder().name("자연진영").colorHex("#43D043").displayOrder(3).build(),
                Faction.builder().name("공학진영").colorHex("#3DA8DE").displayOrder(4).build(),
                Faction.builder().name("예술진영").colorHex("#B08AE0").displayOrder(5).build()
        );
        factionRepository.saveAll(factions);

        // 단과대 매핑 단계에서 진영을 이름으로 찾을 수 있도록 Map 으로 모아 반환.
        return Map.of(
                "인문진영", factions.get(0),
                "사회진영", factions.get(1),
                "자연진영", factions.get(2),
                "공학진영", factions.get(3),
                "예술진영", factions.get(4)
        );
    }

    /**
     * 12개 단과대 + 진영 매핑.
     * 컨텍스트의 표를 그대로 코드화 → 변경 시 여기와 README 두 곳을 같이 맞춰야 함.
     */
    private void seedDepartments(Map<String, Faction> factions) {
        List<Department> departments = List.of(
                // 인문진영 (3)
                Department.builder().name("불교대학").faction(factions.get("인문진영")).build(),
                Department.builder().name("문과대학").faction(factions.get("인문진영")).build(),
                Department.builder().name("사범대학").faction(factions.get("인문진영")).build(),
                // 사회진영 (4)
                Department.builder().name("법과대학").faction(factions.get("사회진영")).build(),
                Department.builder().name("사회과학대학").faction(factions.get("사회진영")).build(),
                Department.builder().name("경찰사법대학").faction(factions.get("사회진영")).build(),
                Department.builder().name("경영대학").faction(factions.get("사회진영")).build(),
                // 자연진영 (2)
                Department.builder().name("이과대학").faction(factions.get("자연진영")).build(),
                Department.builder().name("바이오시스템대학").faction(factions.get("자연진영")).build(),
                // 공학진영 (2)
                Department.builder().name("공과대학").faction(factions.get("공학진영")).build(),
                Department.builder().name("첨단융합대학").faction(factions.get("공학진영")).build(),
                // 예술진영 (1)
                Department.builder().name("예술대학").faction(factions.get("예술진영")).build()
        );
        departmentRepository.saveAll(departments);
    }

    /**
     * 게임 1건 — 오늘 16:00 부터 3일간(즉 Day3 24:00 까지).
     *
     * "오늘" 은 시드가 실행되는 날의 시스템 로컬 날짜(KST). 축제 본 운영 직전엔 이 클래스를
     * 비활성화하거나 startsAt/endsAt 을 고정 날짜로 바꿔야 함 (지금은 학습/테스트 단계).
     *
     * 시작 status: SCHEDULED — 시각 도래 시 ACTIVE 로 전환하는 스케줄러는 추후 단계에서 추가.
     */
    private Game seedGame() {
        LocalDate today = LocalDate.now();                          // 예: 2026-05-16
        LocalDateTime startsAt = today.atTime(8, 0);                 // 08:00 — 운영 일정과 동일 (2026-05-21 16→08 연장)
        // "Day3 의 24:00" = "Day4 의 00:00" = today + 3일의 자정. plusDays(3).atStartOfDay() 가 정확.
        LocalDateTime endsAt = today.plusDays(3).atStartOfDay();     // 2026-05-19 00:00

        Game game = Game.builder()
                .name("2026 동국대 축제 픽셀 점령전")
                .canvasWidth(11)             // application.yml 의 game.canvas.width 와 동일하게 유지 (11×17 = 187셀)
                .canvasHeight(17)
                .cooldownSeconds(5)          // 5초 — application.yml 의 game.cooldown-seconds 와 동일
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(GameStatus.SCHEDULED)
                .build();
        return gameRepository.save(game);
    }

    /**
     * 3개 세션 (Day 1/2/3). 각 세션은 08:00 ~ 다음날 00:00 (= 당일 24:00).
     */
    private void seedSessions(Game game) {
        LocalDate today = LocalDate.now();
        List<GameSession> sessions = List.of(
                buildSession(game, 1, today),
                buildSession(game, 2, today.plusDays(1)),
                buildSession(game, 3, today.plusDays(2))
        );
        gameSessionRepository.saveAll(sessions);
    }

    private GameSession buildSession(Game game, int dayNumber, LocalDate dayDate) {
        return GameSession.builder()
                .game(game)
                .dayNumber(dayNumber)
                .startsAt(dayDate.atTime(8, 0))
                .endsAt(dayDate.plusDays(1).atStartOfDay())   // 당일 24:00 == 다음 날 00:00
                .build();
    }
}
