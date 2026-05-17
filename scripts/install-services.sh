#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# install-services.sh — EC2 안에서 systemd + nginx 설정 일괄 설치.
#
# 사전조건: ~/dotwars.service, ~/docker-compose.prod.yml, ~/nginx-dotwars.conf 업로드됨.
# 이 스크립트 실행 후엔 nginx 만 동작 (dotwars 는 JAR 배포 후 별도 start).
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

echo "[1/5] docker-compose 운영 위치로 이동"
if [ -f ~/docker-compose.prod.yml ]; then
    mv ~/docker-compose.prod.yml /home/ec2-user/app/docker-compose.yml
fi
ls -la /home/ec2-user/app/docker-compose.yml

echo ""
echo "[2/5] systemd 유닛 설치"
if [ -f ~/dotwars.service ]; then
    sudo mv ~/dotwars.service /etc/systemd/system/dotwars.service
    sudo chown root:root /etc/systemd/system/dotwars.service
    sudo chmod 644 /etc/systemd/system/dotwars.service
fi
sudo systemctl daemon-reload
sudo systemctl enable dotwars
echo "  systemd dotwars unit enabled"

echo ""
echo "[3/5] Nginx 설정 설치"
if [ -f ~/nginx-dotwars.conf ]; then
    sudo mv ~/nginx-dotwars.conf /etc/nginx/conf.d/dotwars.conf
    sudo chown root:root /etc/nginx/conf.d/dotwars.conf
    sudo chmod 644 /etc/nginx/conf.d/dotwars.conf
fi

# HTTPS 블록을 통째 주석 처리 - 인증서 발급 전이라
# >>> HTTPS START ~ <<< HTTPS END 마커 사이 모든 라인 앞에 #__ 추가
sudo sed -i '/^# >>> HTTPS START/,/^# <<< HTTPS END/ s/^/#__/' /etc/nginx/conf.d/dotwars.conf
echo "  HTTPS 블록 임시 주석 처리 완료"

# Amazon Linux 기본 default.conf 가 80 포트 점유하면 충돌 방지
if [ -f /etc/nginx/conf.d/default.conf ]; then
    sudo mv /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.disabled
    echo "  default.conf 비활성화"
fi

echo ""
echo "[4/5] Nginx 설정 문법 검증"
sudo nginx -t

echo ""
echo "[5/5] Nginx 시작"
sudo systemctl start nginx
sudo systemctl is-active nginx
echo "  nginx running on port 80"
