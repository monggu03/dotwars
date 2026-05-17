#!/bin/bash
# reset-me.sh — 닉네임으로 사용자의 단과대 선택 초기화 (베타 테스트 자기 자신용).
#
# 호출 방법: scripts/reset-me.ps1 에서 SSH 로 실행됨.
#           NICK_B64 환경변수에 base64 로 인코딩된 닉네임을 받음.
#
# 왜 base64?
#   PowerShell → ssh → bash → mysql -e 까지 quoting 단계가 너무 많아
#   ' 와 한글 인코딩이 깨지기 쉬움. base64 한 단계로 모든 quoting 문제 우회.
#
# 종료 코드:
#   0 = 성공, 1 = 환경 오류, 2 = 매칭 0건, 3 = 매칭 2건 이상

set -euo pipefail

if [ -z "${NICK_B64:-}" ]; then
  echo "ERROR: NICK_B64 env var not set" >&2
  exit 1
fi

NICK=$(echo "$NICK_B64" | base64 -d)

# .env.prod 의 MYSQL_ROOT_PASSWORD 로드 — docker compose 와 동일한 출처
set -a
source /home/ec2-user/app/config/.env.prod
set +a

# SQL 작은따옴표 이스케이프: ' → '\''  (bash parameter expansion)
ESCAPED=${NICK//\'/\'\\\'\'}

# 일치 행 조회 — -N (헤더 없음) -B (탭 구분)
ROWS=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" dotwars-mysql \
  mysql -uroot -N -B -e \
  "SELECT id, kakao_id, nickname, IFNULL(department_id,'NULL') FROM dotwars.users WHERE nickname = '$ESCAPED';")

# 빈 줄 제외 행 카운트 — || true 로 grep 0건 시 set -e 폭주 방지
COUNT=$(printf "%s\n" "$ROWS" | grep -c . || true)

if [ "$COUNT" -eq 0 ]; then
  echo "사용자 없음 — 닉네임 매칭 실패: $NICK" >&2
  echo "(카카오 닉네임 정확히 일치해야 함 — 띄어쓰기/대소문자 구분)" >&2
  exit 2
fi

if [ "$COUNT" -gt 1 ]; then
  echo "동일 닉네임 사용자가 ${COUNT}명 — 어느 한 명을 지정해 직접 처리해주세요:" >&2
  printf "  [id]\t[kakao_id]\t[nickname]\t[department_id]\n" >&2
  printf "  %s\n" "$ROWS" >&2
  exit 3
fi

echo "초기화 전 상태:"
echo "  [id] [kakao_id] [nickname] [department_id]"
printf "  %s\n" "$ROWS"
echo ""

docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" dotwars-mysql \
  mysql -uroot -e \
  "UPDATE dotwars.users SET department_id = NULL WHERE nickname = '$ESCAPED';"

echo "단과대 초기화 완료 — 이 사용자는 다음 로그인 시 단과대 선택 화면으로 다시 진입합니다."
