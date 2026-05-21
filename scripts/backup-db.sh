#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# backup-db.sh — dotwars MySQL 데일리 백업.
#
# 동작:
#  1. docker exec dotwars-mysql 로 mysqldump 실행
#  2. gzip 압축 → /home/ec2-user/backups/dotwars-YYYYMMDD-HHMMSS.sql.gz
#  3. 7일 이상 오래된 백업 자동 삭제 (디스크 절약)
#
# 호출:
#  - cron (/etc/cron.d/dotwars-backup) 매일 04:00 자동
#  - 수동 테스트: bash /home/ec2-user/app/scripts/backup-db.sh
#
# 복원 방법 (사고 시):
#  gunzip < /home/ec2-user/backups/dotwars-YYYYMMDD-HHMMSS.sql.gz | \
#    docker exec -i dotwars-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dotwars
# ─────────────────────────────────────────────────────────────────────

set -e

BACKUP_DIR=/home/ec2-user/backups
RETAIN_DAYS=7
DATE=$(date +%Y%m%d-%H%M%S)
OUT_FILE="$BACKUP_DIR/dotwars-$DATE.sql.gz"

mkdir -p "$BACKUP_DIR"

# .env.prod 에서 MYSQL_ROOT_PASSWORD 로드 (export 처리)
set -a
source /home/ec2-user/app/config/.env.prod
set +a

# mysqldump:
#  --single-transaction: InnoDB 트랜잭션 일관성 (잠금 없이 백업)
#  --routines/--triggers: 저장 프로시저/트리거 포함
#  --default-character-set=utf8mb4: 한글 안 깨짐
docker exec dotwars-mysql mysqldump \
    -uroot -p"$MYSQL_ROOT_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --default-character-set=utf8mb4 \
    dotwars 2>/dev/null | gzip > "$OUT_FILE"

SIZE=$(du -h "$OUT_FILE" | cut -f1)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] backup done: $(basename $OUT_FILE) ($SIZE)"

# 오래된 백업 정리 (7일 초과)
DELETED=$(find "$BACKUP_DIR" -name "dotwars-*.sql.gz" -mtime +$RETAIN_DAYS -print -delete | wc -l)
if [ "$DELETED" -gt 0 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] cleaned up $DELETED old backup(s)"
fi
