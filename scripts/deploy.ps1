# ─────────────────────────────────────────────────────────────────────
# deploy.ps1 — 로컬 빌드 + EC2 배포 자동화.
#
# 사용:
#   .\scripts\deploy.ps1 -EC2_IP 43.200.30.137
#   (필요 시 -KEY_PATH 로 키 경로 명시. 기본은 ~/.ssh/dotwars-key.pem)
#
# 흐름:
#  1. gradlew bootJar (로컬 빌드)
#  2. JAR + docker-compose.prod.yml 업로드
#  3. SSH 로 원격 명령:
#     - docker compose up -d (MySQL/Redis healthy 보장)
#     - systemctl restart dotwars (Spring Boot 재시작)
#     - 최근 로그 30줄 출력
#  4. 헬스체크 (HTTPS → 인증서 발급 전엔 HTTP) 검증
# ─────────────────────────────────────────────────────────────────────

param(
    [Parameter(Mandatory=$true)] [string]$EC2_IP,
    [string]$KEY_PATH = "$env:USERPROFILE\.ssh\dotwars-key.pem",
    # HTTPS 인증서 발급 전엔 -UseHttp 옵션으로 HTTP 헬스체크
    [switch]$UseHttp
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $KEY_PATH)) {
    Write-Host "[ERROR] 키 파일 없음: $KEY_PATH" -ForegroundColor Red
    exit 1
}

Write-Host "=== [1/5] Gradle bootJar 빌드 ===" -ForegroundColor Cyan
& .\gradlew.bat clean bootJar
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 빌드 실패" -ForegroundColor Red
    exit 1
}

# bootJar 결과물 위치 — *-plain.jar (Spring Boot 아닌 일반 jar) 는 제외
$JAR = Get-ChildItem -Path "build/libs" -Filter "*.jar" |
       Where-Object { $_.Name -notmatch "plain" } |
       Select-Object -First 1
if (-not $JAR) {
    Write-Host "[ERROR] bootJar 산출물 못 찾음" -ForegroundColor Red
    exit 1
}
Write-Host "  JAR : $($JAR.FullName) ($([math]::Round($JAR.Length / 1MB, 1)) MB)"

Write-Host ""
Write-Host "=== [2/5] JAR + docker-compose 업로드 ===" -ForegroundColor Cyan
scp -i $KEY_PATH $JAR.FullName "ec2-user@${EC2_IP}:/home/ec2-user/app/dotwars.jar"
scp -i $KEY_PATH scripts/docker-compose.prod.yml "ec2-user@${EC2_IP}:/home/ec2-user/app/docker-compose.yml"

Write-Host ""
Write-Host "=== [3/5] 원격 docker compose up + systemctl restart ===" -ForegroundColor Cyan
# heredoc 으로 한 SSH 세션에 다단계 명령 — 매 명령마다 ssh 새로 여는 것보다 빠름.
ssh -i $KEY_PATH "ec2-user@${EC2_IP}" @'
set -e
cd /home/ec2-user/app

# env 로드 - docker compose 가 MYSQL_ROOT_PASSWORD 등을 export
set -a
source /home/ec2-user/app/config/.env.prod
set +a

docker compose up -d
sudo systemctl restart dotwars

# Spring Boot 부팅 8초 대기 (MySQL 컨테이너 부팅 후 인증 ready)
sleep 8

echo "===== recent log tail ====="
tail -30 logs/app.log 2>/dev/null || echo "no log yet"
'@

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 원격 명령 실패" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== [4/5] 부팅 추가 대기 (10초) ===" -ForegroundColor Cyan
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "=== [5/5] 헬스체크 ===" -ForegroundColor Cyan
$scheme = if ($UseHttp) { "http" } else { "https" }
$url = "${scheme}://dotwars.kr/api/health"
Write-Host "  GET $url"
try {
    $resp = Invoke-RestMethod -Uri $url -TimeoutSec 10
    Write-Host "  ✓ 응답: $($resp | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "  ✗ 헬스체크 실패: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  진단 명령:" -ForegroundColor Yellow
    Write-Host "    ssh -i $KEY_PATH ec2-user@$EC2_IP 'sudo systemctl status dotwars'"
    Write-Host "    ssh -i $KEY_PATH ec2-user@$EC2_IP 'tail -50 /home/ec2-user/app/logs/app-error.log'"
    exit 1
}

Write-Host ""
Write-Host "=== 배포 완료 ===" -ForegroundColor Green
