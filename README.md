# dotwars

동국대학교 축제에서 3일간 운영하는 **픽셀 점령 게임** 백엔드.
50×50 픽셀 캔버스 위에서 단과대별 5개 진영이 영역을 다투는 r/place 미니어처 형식.

학생 한 명당 한 픽셀을 칠하면 5분 쿨다운이 걸리고, 매일 16:00~24:00 운영 후 자정에 캔버스 상태가 동결, 다음날 16:00에 그 상태로 재개. 3일차 자정에 게임 오버, **픽셀 점유율이 가장 높은 진영이 우승**.

Spring Boot 기반으로 트래픽 처리(실시간 동시성, Redis 캐시/락, WebSocket 브로드캐스트, JWT 무상태 인증)를 직접 학습하면서 만드는 프로젝트.

---

## 게임 규칙 요약

| 항목 | 값 |
| --- | --- |
| 캔버스 | 50 × 50 픽셀 |
| 운영 시간 | 매일 **16:00 ~ 24:00**, 총 3일 |
| 쿨다운 | 픽셀 1개 칠하면 **5분** 대기 |
| 자정 처리 | 24:00에 freeze → 다음날 16:00에 그 상태로 재개 |
| 종료 | 3일차 24:00, **최다 픽셀 진영이 우승** |
| 로그인 | 카카오 OAuth (1회 단과대 선택 후 변경 불가) |

### 진영 5개

| 진영 | 색상 | 단과대 |
| --- | --- | --- |
| 인문진영 | 🟧 `#FF7F0E` | 불교대학, 문과대학, 사범대학 |
| 사회진영 | 🟥 `#D62728` | 법과대학, 사회과학대학, 경찰사법대학, 경영대학 |
| 자연진영 | 🟩 `#2CA02C` | 이과대학, 바이오시스템대학 |
| 공학진영 | 🟦 `#1F77B4` | 공과대학, 첨단융합대학 |
| 예술진영 | 🟪 `#9467BD` | 예술대학 |

---

## 기술 스택

| 분류 | 사용 기술 |
| --- | --- |
| 언어 / 런타임 | Java 21 (OpenJDK Microsoft 빌드) |
| 프레임워크 | Spring Boot 3.5.14 |
| 빌드 | Gradle 8.14 (Groovy DSL) |
| DB | MySQL 8.0 (로컬: Docker, 운영: AWS RDS) |
| 캐시 / 실시간 | Redis 7 (로컬: Docker, 운영: AWS ElastiCache) |
| 실시간 통신 | Spring WebSocket + STOMP, Redis Pub/Sub |
| 인증 | 카카오 OAuth 2.0 + JWT (jjwt 0.12.6) |
| 입력 검증 | Spring Validation (Bean Validation) |
| 보일러플레이트 제거 | Lombok |
| 호스팅 | AWS EC2 + RDS + ElastiCache (예정) |
| 도메인 | dotwars.kr |

---

## 로컬 실행

### 사전 요구사항

| 도구 | 버전 | 확인 명령 |
| --- | --- | --- |
| Java | **21** | `java --version` |
| Docker Desktop | 최신 | `docker --version` |
| Git | 2.x | `git --version` |

Java가 21이 아니면 `JAVA_HOME` / PATH 확인 필요. Docker Desktop은 컨테이너를 띄우기 전에 **실행 중**이어야 함.

### 단계별 명령어

```powershell
# 1) 레포 클론
git clone https://github.com/<your-username>/dotwars.git
cd dotwars

# 2) 시크릿 설정 — 템플릿을 복사해서 실제 키 채우기
copy src\main\resources\application-secret.yml.example src\main\resources\application-secret.yml
# 그 후 application-secret.yml 열어서 CHANGE_ME 부분을 실제 값으로 수정:
#  - kakao.client-id / client-secret  → 카카오 디벨로퍼스에서 발급
#  - jwt.secret                       → base64 256bit 이상 랜덤 값

# 3) MySQL + Redis 기동 (백그라운드)
docker compose up -d

# 4) 컨테이너 상태 확인 — 둘 다 "healthy" 가 보일 때까지 기다림
docker compose ps

# 5) Spring Boot 실행
.\gradlew.bat bootRun

# 6) 새 터미널에서 헬스체크 호출
curl http://localhost:8080/api/health
# 예상 응답: {"status":"ok","service":"dotwars","timestamp":"2026-..."}
```

### 정지 / 정리

```powershell
# Spring Boot: Ctrl+C 로 종료
.\gradlew.bat --stop          # gradle daemon 까지 종료

# Docker
docker compose down           # 컨테이너만 정리 (DB 데이터 유지)
docker compose down -v        # 볼륨까지 삭제 (DB 초기화)
```

### JWT 시크릿 생성 (PowerShell)

```powershell
[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 } | % { [byte]$_ }))
```

---

## 폴더 구조

```
dotwars/
├── docker-compose.yml          # 로컬 MySQL 8 + Redis 7
├── build.gradle                # 의존성 (한국어 주석 포함)
├── gradlew / gradlew.bat       # Gradle wrapper
└── src/
    ├── main/
    │   ├── java/com/dongguk/dotwars/
    │   │   ├── auth/           # 카카오 OAuth + JWT
    │   │   │   ├── kakao/      # 카카오 인증 클라이언트
    │   │   │   ├── jwt/        # JWT 발급/검증
    │   │   │   └── controller/
    │   │   ├── common/         # 횡단 관심사
    │   │   │   ├── config/     # SecurityConfig, WebSocketConfig 등
    │   │   │   ├── controller/ # HealthController
    │   │   │   ├── exception/  # 공통 예외/응답
    │   │   │   └── util/
    │   │   ├── user/           # 사용자 도메인
    │   │   ├── department/     # 단과대 / 진영
    │   │   ├── game/           # 게임 / 캔버스 / 픽셀
    │   │   └── DotwarsApplication.java
    │   └── resources/
    │       ├── application.yml                  # 공개 가능한 설정
    │       └── application-secret.yml.example   # 비밀 값 템플릿
    └── test/
```

각 도메인 폴더(user / department / game)는 **`domain` · `repository` · `service` · `controller` · `dto`** 의 5단 구조를 가짐. 표준 패턴을 굳혀두면 새 도메인 추가 시 어디에 무엇을 놓아야 할지 고민 0.

---

## 운영 일정 (예정)

| 단계 | 시점 | 내용 |
| --- | --- | --- |
| D-14 | 2026-05-15 | 프로젝트 초기 셋업 ✅ |
| D-13 ~ D-7 | | 인증 / 도메인 / 캔버스 로직 |
| D-6 ~ D-2 | | WebSocket 실시간 / 부하 테스트 |
| D-1 | | AWS 배포 / 도메인 연결 |
| Day 1~3 | 축제 운영 | 16:00~24:00 |

---

## 라이선스

본 프로젝트는 학습 목적으로 작성됨.
