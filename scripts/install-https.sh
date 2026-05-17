#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# install-https.sh — Let's Encrypt 인증서 발급 + Nginx HTTPS 활성.
#
# 사전조건:
#  - 가비아 DNS A 레코드 (@/www) 가 EC2 EIP 가리킴 (nslookup 확인됨)
#  - Nginx 80 포트 정상 동작 (install-services.sh 끝남)
#  - HTTPS 블록은 현재 #__ 로 주석 처리된 상태
#
# 흐름:
#  1. ACME challenge 디렉토리 생성
#  2. certbot certonly --webroot 로 인증서만 발급 (nginx conf 수정 X)
#  3. HTTPS 블록 주석 해제 (sed)
#  4. nginx -t + reload
#  5. 자동 갱신 cron 등록
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

EMAIL="${1:-leoisthestart@gmail.com}"
DOMAIN="${2:-dotwars.kr}"
WWW_DOMAIN="www.${DOMAIN}"

echo "[1/5] ACME challenge 디렉토리 준비"
sudo mkdir -p /var/www/certbot
sudo chown -R nginx:nginx /var/www/certbot 2>/dev/null || true

echo ""
echo "[2/5] Let's Encrypt 인증서 발급 (${DOMAIN}, ${WWW_DOMAIN})"
# --webroot : nginx 가 /var/www/certbot 의 challenge 파일 서빙. nginx 설정 변경 X.
# --non-interactive + --agree-tos : 자동 진행
# --email : 갱신 알림 / 보안 공지 수신 (90일 주기)
sudo certbot certonly --webroot \
    -w /var/www/certbot \
    -d "${DOMAIN}" -d "${WWW_DOMAIN}" \
    --non-interactive \
    --agree-tos \
    --email "${EMAIL}"

echo ""
echo "[3/5] 발급된 인증서 확인"
sudo certbot certificates

echo ""
echo "[4/5] Nginx HTTPS 블록 주석 해제 + reload"
# install-services.sh 가 박은 #__ 접두사 제거
sudo sed -i 's/^#__//' /etc/nginx/conf.d/dotwars.conf
sudo nginx -t
sudo systemctl reload nginx
echo "  HTTPS 활성. 443 포트 동작."

echo ""
echo "[5/5] 자동 갱신 cron 등록"
# Let's Encrypt 는 90일 만료. certbot renew 가 30일 이내면 갱신 시도.
# 매주 월요일 03:00 KST 체크. --post-hook 으로 갱신 시 nginx reload.
CRON='0 3 * * 1 root /usr/bin/certbot renew --quiet --post-hook "systemctl reload nginx"'
echo "${CRON}" | sudo tee /etc/cron.d/certbot-renew > /dev/null
sudo chmod 644 /etc/cron.d/certbot-renew
echo "  cron /etc/cron.d/certbot-renew 등록 (매주 월 03:00 자동 갱신 시도)"

echo ""
echo "=========================================="
echo "  HTTPS 활성 완료"
echo "  → https://${DOMAIN} 접속 가능"
echo "  → 인증서 만료까지 ~90일, 자동 갱신 예약됨"
echo "=========================================="
