#!/bin/bash
# 운영 DB 의 game/sessions 시각 갱신 + 검증을 한 스크립트로.
set -euo pipefail

set -a
source /home/ec2-user/app/config/.env.prod
set +a

echo "=== applying update-schedule.sql ==="
cat ~/update-schedule.sql | docker exec -i dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dotwars

echo ""
echo "=== verify: game_sessions ==="
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SELECT day_number, starts_at, ends_at FROM game_sessions ORDER BY day_number;"

echo ""
echo "=== verify: games ==="
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SELECT id, starts_at, ends_at, status FROM games;"
