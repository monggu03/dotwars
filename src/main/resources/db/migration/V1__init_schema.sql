-- ─────────────────────────────────────────────────────────────────────
-- V1__init_schema.sql — dotwars 초기 스키마.
--
-- 출처: 운영 EC2 의 MySQL 컨테이너 (Hibernate ddl-auto: update 가 생성한 스키마) 를
--      mysqldump --no-data 로 추출 후 정리.
--
-- 적용 시점:
--  - 신규(빈) DB: Flyway 가 V1 적용 → 7개 테이블 생성
--  - 기존(베타 테스트 데이터 있는) 운영 DB: baseline-on-migrate=true 로 V1 skip
--    + flyway_schema_history 에 baseline 행만 기록
--
-- 테이블 의존 순서 (FK 참조 방향):
--   factions ─→ departments ─→ users
--   games ─→ game_sessions
--   pixel_history / final_results (FK 없음, raw id 만)
-- ─────────────────────────────────────────────────────────────────────

-- ── 진영 ────────────────────────────────────────────────────────────
CREATE TABLE factions (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    color_hex     VARCHAR(7)  NOT NULL,
    display_order INT         NOT NULL,
    name          VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKe5v4rshlkexwkyr9ttmk2ksxl (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 단과대 (FK → factions) ─────────────────────────────────────────
CREATE TABLE departments (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    faction_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKj6cwks7xecs5jov19ro8ge3qk (name),
    KEY FK6gtfneuvs0dnw25vntmv2voav (faction_id),
    CONSTRAINT FK6gtfneuvs0dnw25vntmv2voav FOREIGN KEY (faction_id) REFERENCES factions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 게임 ────────────────────────────────────────────────────────────
CREATE TABLE games (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    canvas_height    INT         NOT NULL,
    canvas_width     INT         NOT NULL,
    cooldown_seconds INT         NOT NULL,
    ends_at          DATETIME(6) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    starts_at        DATETIME(6) NOT NULL,
    status           ENUM('ACTIVE','ENDED','FROZEN','SCHEDULED') NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 게임 세션 (FK → games) ─────────────────────────────────────────
CREATE TABLE game_sessions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    day_number INT         NOT NULL,
    ends_at    DATETIME(6) NOT NULL,
    starts_at  DATETIME(6) NOT NULL,
    game_id    BIGINT      NOT NULL,
    PRIMARY KEY (id),
    KEY FKlg198vj4h7ejkp6n710neylxx (game_id),
    CONSTRAINT FKlg198vj4h7ejkp6n710neylxx FOREIGN KEY (game_id) REFERENCES games (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 사용자 (FK → departments, UNIQUE kakao_id) ─────────────────────
CREATE TABLE users (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6) NOT NULL,
    kakao_id      BIGINT      NOT NULL,
    nickname      VARCHAR(50) NOT NULL,
    department_id BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKk4ycaj27putgcujmehwbsrmmc (kakao_id),
    KEY FKsbg59w8q63i0oo53rlgvlcnjq (department_id),
    CONSTRAINT FKsbg59w8q63i0oo53rlgvlcnjq FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 픽셀 이력 (FK 없음 — 대량 INSERT 경로) ──────────────────────────
CREATE TABLE pixel_history (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    faction_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    x          INT         NOT NULL,
    y          INT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_created (created_at),
    KEY idx_position (x, y)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 최종 결과 (FK 없음 — 종료 시 1회 INSERT 후 READ 만) ───────────
CREATE TABLE final_results (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    calculated_at DATETIME(6) NOT NULL,
    faction_id    BIGINT      NOT NULL,
    game_id       BIGINT      NOT NULL,
    percentage    DOUBLE      NOT NULL,
    pixel_count   INT         NOT NULL,
    rank_position INT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
