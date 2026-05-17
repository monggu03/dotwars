#!/bin/bash
# Flyway 정리 + 새 JAR 으로 안전한 부팅.
# 1) dotwars 정지 (restart loop 중단)
# 2) JAR 안의 baseline-version 확인 (새 JAR 이 들어갔는지)
# 3) flyway_schema_history 삭제
# 4) dotwars 시작
set -euo pipefail

set -a
source /home/ec2-user/app/config/.env.prod
set +a

echo "[1/4] dotwars 정지 (restart loop 중단)"
sudo systemctl stop dotwars
sleep 2

echo ""
echo "[2/4] JAR 안의 baseline-version 확인 (새 JAR 이 1 이어야)"
if command -v unzip >/dev/null 2>&1; then
    unzip -p /home/ec2-user/app/dotwars.jar BOOT-INF/classes/application-prod.yml | grep -E "baseline-version|^  flyway:" || echo "(yml 추출 실패)"
else
    echo "(unzip 미설치 — 스킵)"
fi

echo ""
echo "[3/4] flyway_schema_history 삭제"
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dotwars -e "DROP TABLE IF EXISTS flyway_schema_history"
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SHOW TABLES LIKE 'flyway%'" 2>&1 | grep -v "Using a password" || echo "(history 테이블 없음 — clean)"

echo ""
echo "[4/4] dotwars 시작 + 부팅 12초 대기"
sudo systemctl start dotwars
sleep 12

echo ""
echo "=== 부팅 로그 마지막 20줄 ==="
tail -20 /home/ec2-user/app/logs/app.log

echo ""
echo "=== flyway_schema_history (새로 만들어졌어야) ==="
docker exec dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t dotwars -e "SELECT installed_rank, version, description, success FROM flyway_schema_history" 2>&1 | grep -v "Using a password"

echo ""
echo "=== dotwars 상태 ==="
sudo systemctl is-active dotwars
