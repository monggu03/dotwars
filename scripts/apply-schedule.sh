#!/bin/bash
# 운영 DB 의 game/sessions 시각 갱신 + 검증.
#
# 사용:
#   bash apply-schedule.sh                 # 기본 = update-schedule.sql (본 운영 5/26~28)
#   bash apply-schedule.sh test            # test-schedule.sql (5/17~19 베타)
#   bash apply-schedule.sh <SQL_파일명>     # ~/<SQL_파일명> 사용
set -euo pipefail

set -a
source /home/ec2-user/app/config/.env.prod
set +a

# 인자에 따라 SQL 파일 선택. 별칭 "test" / "prod" 처리.
ARG="${1:-update-schedule.sql}"
case "$ARG" in
    test)        SQL_FILE="$HOME/test-schedule.sql" ;;
    prod|update) SQL_FILE="$HOME/update-schedule.sql" ;;
    /*|./*)      SQL_FILE="$ARG" ;;
    *)           SQL_FILE="$HOME/$ARG" ;;
esac

if [ ! -f "$SQL_FILE" ]; then
    echo "[ERROR] SQL 파일 없음: $SQL_FILE"
    exit 1
fi

echo "=== applying $SQL_FILE ==="
cat "$SQL_FILE" | docker exec -i dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dotwars

echo ""
echo "=== verify: game_sessions ==="
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SELECT day_number, starts_at, ends_at FROM game_sessions ORDER BY day_number;"

echo ""
echo "=== verify: games ==="
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SELECT id, starts_at, ends_at, status FROM games;"
