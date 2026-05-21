#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# monitor.sh — 부하 테스트 중 EC2 실시간 리소스 모니터.
#
# c7i-flex.large (2 vCPU / 4GB) 기준. t3 가 아니라 CPU 크레딧 개념 없음 →
# CPU 사용률이 장시간 100% 에 붙어있는지(=flex 스로틀/한계)로 판단한다.
#
# 출력(1초 간격):
#   시각 | CPU% | 메모리 사용/전체 | Java RSS(MB) | TCP ESTABLISHED 수
# 파일로도 저장: monitor-YYYYMMDD-HHMMSS.log
#
# 실행:  bash ~/monitor.sh        (Ctrl+C 로 종료)
# ─────────────────────────────────────────────────────────────────────

# 고정 파일명 — 자동화/실시간 읽기 편의. tee 대신 직접 append(builtin write → 즉시 flush).
LOG="${MONITOR_LOG:-$HOME/monitor-live.log}"
echo "기록 파일: $LOG"
echo "Ctrl+C 로 종료"
echo "----------------------------------------------------------------------"

trap 'echo ""; echo "[중지] 로그: $LOG"; exit 0' INT

header="시각      | CPU%  | MEM            | JavaRSS | TCP_ESTAB"
echo "$header"
echo "$header" > "$LOG"

while true; do
    ts=$(date '+%H:%M:%S')

    # CPU 사용률 — vmstat 로 1초 샘플의 idle 을 받아 100-idle.
    # (vmstat 1 2 는 1초 간격 2샘플 → 2번째가 그 1초의 평균. 이게 곧 루프 간격이 됨)
    idle=$(vmstat 1 2 | tail -1 | awk '{print $15}')
    cpu=$((100 - idle))

    # 메모리 — 사용/전체 MB + 퍼센트
    mem=$(free -m | awk '/Mem:/ {printf "%d/%dMB(%d%%)", $3, $2, ($3/$2)*100}')

    # Java(Spring Boot) 프로세스 RSS 합 (MB)
    java_rss=$(ps -C java -o rss= 2>/dev/null | awk '{s+=$1} END {printf "%dMB", s/1024}')
    [ -z "$java_rss" ] && java_rss="-"

    # 활성 TCP 연결 (ESTABLISHED) — 동접 부하의 직접 지표
    tcp=$(ss -tan 2>/dev/null | grep -c ESTAB)

    # echo(stdout, 수동 tty 용) + 직접 append(파일, 즉시 flush — tee 의 block-buffer 회피)
    line=$(printf "%s | %3s%% | %-14s | %-7s | %s" "$ts" "$cpu" "$mem" "$java_rss" "$tcp")
    echo "$line"
    echo "$line" >> "$LOG"
done
