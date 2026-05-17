-- 게임 진행 데이터 초기화 (테스트 후 깨끗한 상태로 복원용).
-- 보존: factions, departments, games, game_sessions (마스터 데이터)
-- 삭제: users, pixel_history, final_results (게임 진행 중 쌓이는 데이터)
--
-- 안전 장치: TRUNCATE 대신 DELETE — pixel_history 가 한 user_id 를 참조하지만
-- FK 가 아닌 raw Long 이라 cascade 걱정 없음. AUTO_INCREMENT 도 리셋 안 되는 게
-- 안전 (행 사이 재사용으로 인한 데이터 혼동 방지).

DELETE FROM pixel_history;
DELETE FROM final_results;
DELETE FROM users;

-- 검증
SELECT COUNT(*) AS users_count FROM users;
SELECT COUNT(*) AS pixel_count FROM pixel_history;
SELECT COUNT(*) AS final_count FROM final_results;
