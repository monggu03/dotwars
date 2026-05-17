#!/bin/bash
# ─────────────────────────────────────────────────────────────────────
# ec2-setup.sh — EC2 Amazon Linux 2023 초기 설정.
#
# 설치하는 것:
#   - Java 21 (Amazon Corretto) — Spring Boot JAR 실행 런타임
#   - Docker + docker compose v2 plugin — MySQL/Redis 컨테이너
#   - Nginx — HTTPS 종료 + 8080 프록시
#   - Certbot (Python venv) — Let's Encrypt 인증서 자동 발급/갱신
#   - 시간대 KST + 작업 디렉토리 + 로그 폴더
#
# 실행 후 docker 권한 적용 위해 반드시 SSH 재접속 필요.
# ─────────────────────────────────────────────────────────────────────

set -euo pipefail

echo "=========================================="
echo "[1/7] 시스템 업데이트"
echo "=========================================="
sudo dnf update -y

echo ""
echo "=========================================="
echo "[2/7] Java 21 (Amazon Corretto)"
echo "=========================================="
sudo dnf install -y java-21-amazon-corretto-headless
java -version

echo ""
echo "=========================================="
echo "[3/7] Docker + docker compose v2 plugin"
echo "=========================================="
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# docker compose v2 는 cli plugin 형태. 표준 위치는 /usr/local/lib/docker/cli-plugins.
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 검증 — sudo 로 한 번. 권한 적용은 재로그인 후.
sudo docker --version
sudo docker compose version

echo ""
echo "=========================================="
echo "[4/7] Nginx"
echo "=========================================="
sudo dnf install -y nginx
sudo systemctl enable nginx
nginx -v

echo ""
echo "=========================================="
echo "[5/7] Certbot (Let's Encrypt) — Python venv 격리"
echo "=========================================="
# Amazon Linux 2023 의 dnf 에는 certbot 패키지가 없어서 pip + venv 로.
# venv 로 격리해 시스템 Python 환경 오염 방지.
sudo dnf install -y python3-pip augeas-libs
sudo python3 -m venv /opt/certbot/
sudo /opt/certbot/bin/pip install --upgrade pip
sudo /opt/certbot/bin/pip install certbot certbot-nginx
sudo ln -sf /opt/certbot/bin/certbot /usr/bin/certbot
certbot --version

echo ""
echo "=========================================="
echo "[6/7] 시간대 — Asia/Seoul (KST)"
echo "=========================================="
sudo timedatectl set-timezone Asia/Seoul
date

echo ""
echo "=========================================="
echo "[7/7] 작업 디렉토리"
echo "=========================================="
mkdir -p /home/ec2-user/app/logs
mkdir -p /home/ec2-user/app/config
ls -la /home/ec2-user/app

echo ""
echo "=========================================="
echo "  완료 — SSH 재접속 필요 (docker 권한 적용)"
echo "=========================================="
echo "  로그아웃 후 다시:"
echo "    ssh -i ~/.ssh/dotwars-key.pem ec2-user@<EIP>"
echo "  재접속 후 'docker ps' 가 sudo 없이 동작해야 정상."
