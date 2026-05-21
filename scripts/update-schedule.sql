-- 축제 일정 갱신: 2026-05-26 ~ 2026-05-28 매일 08-24시 KST.
-- 기존 데이터(users / pixel_history 등) 는 보존, game/sessions 시각만 변경.

UPDATE games
   SET starts_at = '2026-05-26 08:00:00',
       ends_at   = '2026-05-29 00:00:00'
 WHERE id = 1;

UPDATE game_sessions SET starts_at='2026-05-26 08:00:00', ends_at='2026-05-27 00:00:00' WHERE day_number=1;
UPDATE game_sessions SET starts_at='2026-05-27 08:00:00', ends_at='2026-05-28 00:00:00' WHERE day_number=2;
UPDATE game_sessions SET starts_at='2026-05-28 08:00:00', ends_at='2026-05-29 00:00:00' WHERE day_number=3;
