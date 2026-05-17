# ─────────────────────────────────────────────────────────────────────
# reset-me.ps1 — 본인 사용자의 단과대 선택 초기화 (베타 테스트용).
#
# 사용:
#   .\scripts\reset-me.ps1 -KakaoNickname '내닉네임'
#   .\scripts\reset-me.ps1 -KakaoNickname '내닉네임' -EC2_IP 43.200.30.137
#
# 동작:
#   1. scripts/reset-me.sh 를 EC2 /tmp/ 로 scp 업로드 (매 실행마다 — 단일 파일 빠름)
#   2. SSH 실행 — 닉네임은 base64 env 로 전달 (한글/따옴표 안전)
#   3. 결과 표시 + 다음 단계 안내
#
# 왜 쿠키는 자동으로 안 비우는가?
#   POST /api/auth/logout 은 브라우저 쿠키만 비울 수 있음 — 서버에선 못 함.
#   가장 깨끗한 방법은 시크릿창. 일반창에서 logout 호출해도 같은 카카오 캐시로
#   바로 재로그인 되어 "처음 사용자" 흐름이 보이지 않을 수 있음.
# ─────────────────────────────────────────────────────────────────────

param(
    [Parameter(Mandatory=$true)] [string]$KakaoNickname,
    [string]$EC2_IP = "43.200.30.137",
    [string]$KEY_PATH = "$env:USERPROFILE\.ssh\dotwars-key.pem"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $KEY_PATH)) {
    Write-Host "[ERROR] 키 파일 없음: $KEY_PATH" -ForegroundColor Red
    exit 1
}

$LocalSh = Join-Path $PSScriptRoot "reset-me.sh"
if (-not (Test-Path $LocalSh)) {
    Write-Host "[ERROR] reset-me.sh 없음: $LocalSh" -ForegroundColor Red
    exit 1
}

Write-Host "=== 단과대 초기화 ===" -ForegroundColor Cyan
Write-Host "  대상 닉네임 : $KakaoNickname"
Write-Host "  EC2        : $EC2_IP"
Write-Host ""

# 1) reset-me.sh 업로드 — scp 가 바이너리 복사라 한글/줄바꿈 안전
scp -i $KEY_PATH $LocalSh "ec2-user@${EC2_IP}:/tmp/reset-me.sh"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] scp 실패" -ForegroundColor Red
    exit 1
}

# 2) 닉네임을 UTF-8 → base64 → 안전한 ASCII 문자열로 변환.
#    PowerShell/SSH/Bash 사이 여러 escape 단계를 base64 한 줄로 우회.
$NickB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($KakaoNickname))

# 3) 원격 실행 — NICK_B64 환경변수로 bash 스크립트에 전달
ssh -i $KEY_PATH "ec2-user@${EC2_IP}" "NICK_B64='$NickB64' bash /tmp/reset-me.sh"
$rc = $LASTEXITCODE

if ($rc -ne 0) {
    Write-Host ""
    switch ($rc) {
        2 { Write-Host "[FAIL] 닉네임 매칭 실패 — 정확한 카카오 닉네임을 확인하세요" -ForegroundColor Red }
        3 { Write-Host "[FAIL] 동일 닉네임 사용자가 여러 명 — 위 목록에서 골라 SSH 로 직접 UPDATE 하세요" -ForegroundColor Red }
        default { Write-Host "[FAIL] 초기화 실패 (exit $rc)" -ForegroundColor Red }
    }
    exit $rc
}

Write-Host ""
Write-Host "다음 단계 (쿠키도 같이 비워야 첫 사용자 흐름이 보임):" -ForegroundColor Yellow
Write-Host "  1. 브라우저 시크릿창 열기 (Ctrl+Shift+N)"
Write-Host "  2. https://dotwars.kr 접속"
Write-Host "  3. 카카오 로그인 → 단과대 선택부터 처음부터 재현"
