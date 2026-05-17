-- 베타 테스트 일정 (2026-05-17 ~ 19) — 친구들과 압축 테스트용.
-- 5/19 24:00 이후 다시 update-schedule.sql 로 본 운영(5/26~28) 일정 복원 필요.
--
-- 보존: 그 외 모든 데이터.

UPDATE games
   SET starts_at = '2026-05-17 16:00:00',
       ends_at   = '2026-05-20 00:00:00'
 WHERE id = 1;

UPDATE game_sessions SET starts_at='2026-05-17 16:00:00', ends_at='2026-05-18 00:00:00' WHERE day_number=1;
UPDATE game_sessions SET starts_at='2026-05-18 16:00:00', ends_at='2026-05-19 00:00:00' WHERE day_number=2;
UPDATE game_sessions SET starts_at='2026-05-19 16:00:00', ends_at='2026-05-20 00:00:00' WHERE day_number=3;
