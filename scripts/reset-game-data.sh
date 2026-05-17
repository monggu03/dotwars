#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# reset-game-data.sh — 게임 진행 데이터 초기화.
#
# 사용 시점:
#  - 친구들과 베타 테스트 후, 진짜 축제 직전 (5/26 16:00 이전) 1회 실행
#  - 또는 테스트 중 캔버스 초기화하고 싶을 때
#
# 영향:
#  - MySQL: users / pixel_history / final_results 모두 비움
#    → 사용자가 다시 카카오 로그인 + 단과대 선택부터
#  - Redis: canvas:current / faction:count / cooldown:user:* 모두 삭제
#    → 캔버스 완전 흰색, 진영 카운트 0, 모든 쿨다운 해제
#  - 보존: factions / departments / games / game_sessions (마스터)
#         game:status (스케줄러가 관리, 손대지 않음)
#
# 안전:
#  - 운영 DB 만 영향. 로컬 DB 와 무관.
#  - 마스터 데이터 (5진영 / 12단과대 / 게임 일정) 변경 X.
#  - 인증/JWT 시크릿 등 설정 변경 X.
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

set -a
source /home/ec2-user/app/config/.env.prod
set +a

echo "=========================================="
echo "  게임 데이터 초기화 시작"
echo "=========================================="

echo ""
echo "[1/4] MySQL — users / pixel_history / final_results 비움"
cat ~/reset-game-data.sql | docker exec -i dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars

echo ""
echo "[2/4] Redis — 캔버스 / 진영 카운트 삭제"
docker exec dotwars-redis redis-cli DEL canvas:current
docker exec dotwars-redis redis-cli DEL faction:count

echo ""
echo "[3/4] Redis — 모든 사용자 쿨다운 삭제 (cooldown:user:*)"
# redis-cli 의 --scan + xargs DEL 패턴. KEYS 대신 SCAN — 운영 안전.
docker exec dotwars-redis sh -c '
COUNT=$(redis-cli --scan --pattern "cooldown:user:*" | wc -l)
if [ "$COUNT" -gt 0 ]; then
  redis-cli --scan --pattern "cooldown:user:*" | xargs -r redis-cli DEL > /dev/null
fi
echo "  deleted cooldown keys: $COUNT"
'

echo ""
echo "[4/4] Redis stats 캐시 무효화 (1초 TTL 이라 자동 만료지만 명시적으로)"
docker exec dotwars-redis redis-cli DEL stats:factions:cache

echo ""
echo "=========================================="
echo "  초기화 완료"
echo "  → 다음 사용자가 카카오 로그인 시 신규 가입"
echo "  → 캔버스 완전 흰색, 모든 쿨다운 해제"
echo "  → game:status 는 그대로 (스케줄러가 매초 갱신)"
echo "=========================================="
