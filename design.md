# Design System for dotwars

> **컨셉**: Pixel Brutalism
> 검은 OS 안에 떠 있는 흰 캔버스. 캔버스가 게임의 전부고 UI는 보조 장비.
> Linear/Vercel의 미니멀리즘 + r/place의 픽셀 감성을 합친 톤.

---

## 디자인 원칙

1. **캔버스가 주인공.** 다른 모든 UI는 "캔버스를 위한 프레임" 역할만.
2. **어두운 배경 위 강렬한 색.** 진영 5색은 채도를 절대 낮추지 않음.
3. **픽셀 그리드 위의 모든 것.** 4px 단위로만 측정. 둥근 모서리는 4px만.
4. **장식 없는 정직함.** 그라데이션, 그림자, 일러스트 안 씀.
5. **모바일 풀스크린 우선.** 1px도 낭비 없게.

---

## 색상 팔레트

### 시스템 (배경/텍스트/보더)

```css
--bg-base:       #0A0A0A;  /* 페이지 배경, 거의 검정 */
--bg-elevated:   #141414;  /* 카드/모달 등 한 단계 올라간 표면 */
--bg-overlay:    rgba(0, 0, 0, 0.85);  /* 모달 뒷 오버레이 */

--border-default: #2A2A2A;  /* 일반 보더 */
--border-strong:  #3D3D3D;  /* 강조 보더 (호버 등) */

--text-primary:   #FAFAFA;  /* 본문 */
--text-secondary: #A0A0A0;  /* 보조 */
--text-tertiary:  #606060;  /* 비활성/메타 */

--white-canvas:   #FFFFFF;  /* 캔버스 빈 픽셀 (절대 변경 X) */
```

### 진영 색 (절대 변경 금지)

```css
--faction-humanities: #FF7F0E;  /* 인문진영 (주황) */
--faction-social:     #D62728;  /* 사회진영 (빨강) */
--faction-natural:    #2CA02C;  /* 자연진영 (초록) */
--faction-engineering:#1F77B4;  /* 공학진영 (파랑) */
--faction-arts:       #9467BD;  /* 예술진영 (보라) */
```

**사용 규칙:**
- 진영 색은 항상 100% 채도/명도로 표시. 어두운 배경에서 빛나도록.
- 호버/포커스 시에도 색 조정 X. 보더만 추가하거나 명도만 미세 조정.
- 텍스트 색으로 쓸 때 검정 배경 대비 충분 (모두 WCAG AA 이상).

### 시스템 색 (상태 표시)

```css
--status-success: #10B981;  /* 픽셀 칠하기 성공 */
--status-warning: #F59E0B;  /* 쿨다운, 곧 마감 */
--status-error:   #EF4444;  /* 에러, 쿨다운 활성 */
--status-info:    #3B82F6;  /* 안내 */
```

---

## 타이포그래피

### 폰트 패밀리

```css
/* 본문 (산세리프) */
--font-sans: 'Pretendard', -apple-system, BlinkMacSystemFont,
             system-ui, 'Apple SD Gothic Neo', sans-serif;

/* 픽셀/모노 (타이틀, 숫자, 카운트다운) */
--font-pixel: 'DOSGothic', 'DotumChe', 'Press Start 2P', monospace;
```

**Pretendard CDN 임포트:**
```html
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.css">
```

**픽셀 폰트는 OS 기본 폰트로 우선:**
- Windows: `DOSGothic`, `DotumChe`
- 없으면 Google Fonts `Press Start 2P` 폴백

### 사이즈/굵기

```css
/* 본문용 */
--text-xs:   11px;  /* 메타정보, caption */
--text-sm:   13px;  /* 보조 텍스트 */
--text-base: 15px;  /* 본문 (모바일 최소 16px 권장이라 거의 16) */
--text-lg:   18px;  /* 강조 본문 */
--text-xl:   24px;  /* 섹션 제목 */
--text-2xl:  32px;  /* 페이지 제목 */

/* 픽셀 폰트용 (큰 숫자/카운트다운) */
--text-pixel-md: 20px;
--text-pixel-lg: 40px;  /* "GAME OVER" 같은 거 */

--weight-regular: 400;
--weight-medium:  500;
--weight-semibold: 600;
--weight-bold:    700;
```

**사용 가이드:**
- 페이지 제목: `--text-2xl` + `semibold`
- 본문: `--text-base` + `regular`
- 카운트다운 숫자: `--font-pixel` + `--text-pixel-md`
- 진영명: `--font-pixel` + `--text-sm` (살짝 게임 느낌)

---

## 간격 (4px 그리드)

```css
--space-1:  4px;
--space-2:  8px;
--space-3:  12px;
--space-4:  16px;  /* 컴포넌트 기본 패딩 */
--space-6:  24px;
--space-8:  32px;
--space-12: 48px;
--space-16: 64px;
```

**금지:** 5px, 7px, 13px 같은 4의 배수 아닌 값. 픽셀 그리드 깨짐.

---

## 둥근 모서리

```css
--radius-sm: 2px;   /* 작은 태그/배지 */
--radius-md: 4px;   /* 버튼, 입력, 카드 (기본) */
--radius-lg: 8px;   /* 모달 (예외적으로만) */
```

**기본은 `--radius-md` (4px). "둥글둥글한" 느낌 절대 X.**

---

## 그림자

**기본적으로 안 씀.** 어두운 배경에서 그림자는 거의 안 보임 + 픽셀 미학 깸.

예외:
```css
--shadow-modal: 0 12px 40px rgba(0, 0, 0, 0.6);  /* 모달만 */
```

깊이 표현은 **`--bg-elevated` (배경색 차이)** 로 해결.

---

## 애니메이션

```css
--transition-fast: 100ms ease-out;  /* hover, active */
--transition-base: 180ms ease-out;  /* 일반 상태 변화 */
--transition-slow: 320ms ease-out;  /* 모달, 페이지 전환 */
```

**금지:**
- `ease-in-out` (부드러워서 느려 보임)
- 1초 넘는 애니메이션 (사용자 짜증)
- bounce, spring 효과 (장식적)

**픽셀 게임 특수:**
- 픽셀 칠해질 때: 짧은 깜빡임 정도 (100ms)
- 카운트다운 마지막 5초: 1초마다 큰 박동

---

## 컴포넌트 가이드

### 버튼

#### Primary Button (메인 액션)
```css
.btn-primary {
  background: var(--text-primary);  /* 흰색 배경 */
  color: var(--bg-base);             /* 검은 텍스트 */
  border: none;
  padding: 12px 24px;
  border-radius: var(--radius-md);
  font-weight: var(--weight-semibold);
  font-size: var(--text-base);
  cursor: pointer;
  transition: var(--transition-fast);
}
.btn-primary:hover {
  background: var(--text-secondary);
}
.btn-primary:active {
  transform: translateY(1px);
}
.btn-primary:disabled {
  background: var(--border-default);
  color: var(--text-tertiary);
  cursor: not-allowed;
}
```

#### Secondary Button
```css
.btn-secondary {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  /* 나머지 동일 */
}
.btn-secondary:hover {
  border-color: var(--border-strong);
}
```

#### 카카오 로그인 버튼 (예외 — 카카오 가이드 따름)
```css
.btn-kakao {
  background: #FEE500;
  color: #181600;
  /* 카카오 공식 색상 사용 의무 */
}
```

### 카드 (진영 정보, 단과대 항목 등)

```css
.card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

/* 진영 색은 왼쪽 4px 라인으로만 표시 */
.card-faction {
  border-left: 4px solid var(--faction-color);  /* JS로 색 주입 */
}
```

**금지:** 카드 배경 전체를 진영 색으로 칠하기. 시각 공해 + 가독성 ↓

### 입력 필드

```css
.input {
  background: var(--bg-base);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  color: var(--text-primary);
  font-size: var(--text-base);
}
.input:focus {
  outline: none;
  border-color: var(--text-primary);
}
```

### 모달 / 시트

```css
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  z-index: 100;
}
.modal-content {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);  /* 예외적으로 8px */
  box-shadow: var(--shadow-modal);
  padding: var(--space-6);
}
```

### 진영 배지 (작은 색 표시)

```css
.faction-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-family: var(--font-pixel);
  font-size: var(--text-xs);
}
.faction-badge::before {
  content: '';
  width: 8px;
  height: 8px;
  background: var(--faction-color);
  /* 둥글지 않은 정사각형 = 픽셀 느낌 */
}
```

### 캔버스 (게임 메인)

```css
.canvas-wrapper {
  background: var(--white-canvas);  /* 흰색, 절대 변경 X */
  width: 100vw;
  height: 100vh;  /* 모바일 풀스크린 */
  position: relative;
}

.canvas {
  image-rendering: pixelated;  /* 픽셀 흐려짐 방지 (중요!) */
  image-rendering: -moz-crisp-edges;
  image-rendering: crisp-edges;
}

/* 미세한 격자선 (선택) */
.canvas-grid {
  background-image:
    linear-gradient(to right, rgba(0,0,0,0.05) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(0,0,0,0.05) 1px, transparent 1px);
  background-size: 1.96vw 1.96vw;  /* 100vw/51 */
}
```

### 상단 미니바 (게임 화면)

```css
.minibar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  padding: 8px 12px;
  background: rgba(10, 10, 10, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border-default);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-pixel);
  font-size: var(--text-xs);
  color: var(--text-primary);
  z-index: 10;
}
```

### 카운트다운 (큰 숫자)

```css
.countdown-large {
  font-family: var(--font-pixel);
  font-size: var(--text-pixel-lg);
  font-weight: var(--weight-bold);
  letter-spacing: 0.05em;
  color: var(--text-primary);
}

.countdown-large.warning {
  color: var(--status-warning);
  animation: pulse 1s ease-in-out infinite;
}

.countdown-large.critical {
  color: var(--status-error);
  animation: pulse 0.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}
```

---

## 페이지별 가이드

### 1. index.html (메인 / 카카오 로그인)

레이아웃:
```
┌─────────────────────────┐
│                         │
│        [DOTWARS]        │  <- 픽셀 폰트 큰 로고
│                         │
│   동국대학교 픽셀 전쟁    │  <- 부제, 일반 폰트
│                         │
│      [게임 룰 안내 3줄]   │  <- 작은 텍스트
│                         │
│                         │
│  [🟡 카카오로 시작하기]   │  <- 노란 버튼
│                         │
│   © 2026 dotwars.kr     │
└─────────────────────────┘
```

- 세로 가운데 정렬
- 로고: 큰 픽셀 폰트, 진영 5색 중 하나로 (랜덤 또는 고정)
- 카카오 버튼은 화면 하단 1/3 지점에 배치 (엄지 닿기 좋게)

### 2. select-department.html (단과대 선택)

레이아웃:
```
┌─────────────────────────┐
│ ← 본인 단과대 선택       │  <- 상단 헤더
├─────────────────────────┤
│ ▌인문진영              │  <- 진영 헤더 (왼쪽 색 바)
│   ○ 불교대학            │
│   ○ 문과대학            │
│   ○ 사범대학            │
├─────────────────────────┤
│ ▌사회진영              │
│   ○ 법과대학            │
│   ...                  │
├─────────────────────────┤
│   ...                  │
├─────────────────────────┤
│ ☐ 본인은 동국대학교       │  <- 약속 체크박스
│   재학생이며 위 단과대가  │
│   본인 소속입니다.       │
│                         │
│   [게임 시작]            │  <- 비활성화 기본
└─────────────────────────┘
```

- 진영별 그룹: 왼쪽 `border-left: 4px solid var(--faction-color)`
- 단과대 라디오: 큰 터치 영역 (최소 44px 높이)
- 선택된 단과대: 본인 진영 색 보더로 강조
- 약속 + 단과대 둘 다 선택해야 게임 시작 버튼 활성화

### 3. game.html (메인 게임)

이건 STEP 5에서 자세히. 일단 원칙만:
- 풀스크린 캔버스 (가장자리까지)
- 상단 미니바만 (반투명, 자동 숨김 가능)
- 우측 하단 플로팅 버튼 (전체 순위 모달)
- 픽셀 클릭 시 확대 시트 (하단 슬라이드업)

---

## 모바일 우선 (가장 중요)

- 모든 클릭 영역 **최소 44×44px** (Apple HIG)
- 텍스트 **최소 14px** (iOS 자동 줌인 방지하려면 16px)
- viewport meta:
  ```html
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
  ```
- safe-area-inset 처리 (아이폰 노치):
  ```css
  padding-top: env(safe-area-inset-top);
  padding-bottom: env(safe-area-inset-bottom);
  ```

---

## 데스크탑 (보조)

- 모바일 디자인을 가운데 정렬, 양 옆에 검은 배경
- 최대 너비: 480px (모바일 시뮬레이션)
- 데스크탑 전용 UI는 만들지 않음 (시간 낭비)

```css
.app-container {
  max-width: 480px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--bg-base);
}
```

---

## CSS 파일 구조

```
src/main/resources/static/
  css/
    tokens.css       <- CSS 변수 (위 색상/간격 모두)
    base.css         <- reset, body, 공통
    components.css   <- 버튼, 카드, 입력 등
    pages/
      index.css
      select-department.css
      game.css
  js/
    ...
  index.html
  select-department.html
  game.html
```

`tokens.css`를 모든 페이지에서 임포트. 변수는 한 곳에서 관리.

---

## ❌ 절대 금지 (LLM이 자주 하는 실수)

1. **그라데이션** — 특히 `from-purple-500 to-pink-500` 같은 거. 어디에도 X.
2. **무지개색** — 우리 색은 5진영 + 시스템 색뿐.
3. **이모지 남발** — 본문에 이모지 X. 필요한 경우만 (카카오 노란 동그라미 정도).
4. **둥근 카드 안의 둥근 카드** — 둥근 거 중첩하지 말기.
5. **장식 일러스트** — 의미 없는 그림 안 그림.
6. **"Powered by..." 푸터** — 자기 자랑 안 함.
7. **"Lorem ipsum"** — 진짜 게임 텍스트로 채울 것.
8. **bouncing/spring 애니메이션** — 미니멀리즘 깨짐.
9. **카드마다 다른 색 배경** — 진영 색은 라인으로만.
10. **사용자 닉네임 표시** — 우리는 익명 게임. 닉네임 어디에도 X.

---

## 빠른 체크리스트 (Claude Code에 던질 때)

```
이 design.md를 참고해서 [페이지명]을 만들어줘.
- tokens.css의 CSS 변수만 사용
- 절대 금지 사항 10가지 지키기
- 모바일 우선, 데스크탑은 가운데 정렬
- 픽셀 폰트는 OS 기본 → 폴백
- 본인 단과대 색은 JS로 동적 주입 (faction-color CSS 변수)
```
