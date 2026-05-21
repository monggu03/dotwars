/*
 * game.js — r/place 식 풀스크린 캔버스 게임.
 *
 * 변경 (2026-05-19):
 *  - 캔버스 11×17 = 187셀로 축소. 모든 폰에 한 눈에 들어옴 (panzoom 제거).
 *  - panzoom 라이브러리 제거 — 줌/팬 자체가 사라짐. 1탭 선택 → 재탭 페인트 그대로.
 *  - resize 시 더 이상 panzoom reset 불필요, pixel-cursor 위치만 갱신.
 */

import { apiGet, apiPost, ApiError } from './api.js';
import { requireLoginWithDepartment } from './auth-guard.js';
import { connectWebSocket } from './websocket.js';

// ── 상수 ────────────────────────────────────────────────────────
// 캔버스 그리드 — 11:17 비율, 187셀. application.yml 의 game.canvas.* 와 일치 필요.
const GRID_WIDTH = 11;
const GRID_HEIGHT = 17;
const CELL_PX = 30;             // 셀당 캔버스 픽셀 (해상도 330x510)
const CANVAS_W = GRID_WIDTH * CELL_PX;
const CANVAS_H = GRID_HEIGHT * CELL_PX;
const GRID_STROKE = 'rgba(0, 0, 0, 0.35)';   // 격자선 — Game Vibrant 팔레트 위에서도 명확히 보임 (2026-05-19)
// 통계는 WebSocket 으로 안 보내고 폴링 유지 — 부하 크고 실시간성 덜 중요.
// 3초 → 5초로 늘림 (WebSocket 으로 picks 즉시 갱신되니까 굳이 짧을 필요 없음).
const STATS_POLL_MS = 5000;
const COOLDOWN_TICK_MS = 1000;
const DOUBLE_TAP_WINDOW_MS = 400;   // 같은 칸 재탭으로 인정하는 시간 창

// Game Vibrant 팔레트 (2026-05-19) — DB factions.color_hex / tokens.css 와 반드시 동기화.
const FACTION_COLORS = {
    0: '#FFFFFF',
    1: '#FFA040',   // 인문 주황
    2: '#F04545',   // 사회 빨강
    3: '#43D043',   // 자연 초록
    4: '#3DA8DE',   // 공학 파랑
    5: '#B08AE0',   // 예술 보라
};

// ── DOM ─────────────────────────────────────────────────────────
const $ = (id) => document.getElementById(id);

const els = {
    canvas: $('game-canvas'),
    stage: $('canvas-stage'),
    wrapper: $('canvas-wrapper'),
    pixelCursor: $('pixel-cursor'),
    coordBar: $('coord-bar'),
    coordValue: $('coord-value'),
    cancelSelect: $('cancel-select'),
    myFactionDot: $('my-faction-dot'),
    myFactionName: $('my-faction-name'),
    // 마이페이지(내 픽셀 발자취) 모달
    myFactionBtn: $('my-faction-btn'),
    paintsModal: $('paints-modal'),
    closePaints: $('close-paints'),
    paintsSummary: $('paints-summary'),
    paintsList: $('paints-list'),
    paintsMeta: $('paints-meta'),
    cooldown: $('cooldown-timer'),
    // 상단 진영 점유율 막대 — 미니바 대체. 탭=모달.
    factionBar: $('faction-bar'),
    statsModal: $('stats-modal'),
    closeStats: $('close-stats'),
    statsList: $('stats-list'),
    statsMeta: $('stats-meta'),
};

// ── 모듈 상태 ────────────────────────────────────────────────────
let me = null;
let ctx = null;
let cooldownEndsAt = null;
// 더블탭 감지 — 마지막 1탭의 좌표와 시각
let lastTap = { x: -1, y: -1, time: 0 };
// 현재 선택된 칸 (1탭으로 선택, 페인트하거나 다른 칸 클릭하거나 취소까지 유지)
let selected = null;   // { x, y } | null

init().catch((e) => console.error('[game] 초기화 실패', e));

// ─────────────────────────────────────────────────────────────────
//  초기화
// ─────────────────────────────────────────────────────────────────
async function init() {
    me = await requireLoginWithDepartment();
    if (!me) return;

    document.documentElement.style.setProperty('--my-faction-color', me.faction.colorHex);
    els.myFactionDot.style.background = me.faction.colorHex;
    els.myFactionName.textContent = me.faction.name;

    ctx = els.canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;   // pixel-perfect 그리기
    fillCanvasWhite();
    drawGrid();

    // 게임 화면은 ACTIVE 일 때만 의미가 있음.
    // ENDED → 결과 발표 화면, FROZEN/SCHEDULED → 휴식 화면(별도 페이지) 로 분기.
    const status = await apiGet('/api/game/status').catch(() => null);
    if (status?.status === 'ENDED') {
        location.replace('/game-end.html');
        return;
    }
    if (status && status.status !== 'ACTIVE') {
        location.replace('/waiting.html');
        return;
    }

    await Promise.all([
        loadCanvas(),
        loadInitialCooldown(),
        loadStats(),
    ]);

    setupTapHandler();
    setupModalHandlers();
    setupResizeHandler();
    showTutorialIfFirstTime();

    // ── WebSocket 연결 ──────────────────────────────────────────
    // 캔버스 초기 로드 후 실시간 수신 시작 → 다른 사람이 칠한 픽셀이 1초 이내 화면에 반영.
    connectWebSocket({
        onPixelPainted: (msg) => {
            // 본인이 보낸 페인트의 echo 도 들어옴 (자기 메시지). 같은 색 한 번 더 그리기 = 무해.
            enqueuePaintedPixel(msg.x, msg.y, msg.factionId);
        },
        onGameStatusChanged: (msg) => {
            // 게임 라이프사이클 전환을 폴링 없이 즉시 반영
            if (msg.status === 'ENDED') {
                location.replace('/game-end.html');
                return;
            }
            if (msg.status !== 'ACTIVE') {
                // FROZEN / SCHEDULED — 24:00 freeze 등. 휴식 화면으로 이동.
                location.replace('/waiting.html');
            }
        },
        onReconnected: () => {
            // 재연결 동안 누락된 픽셀이 있을 수 있으므로 캔버스 다시 받기.
            // STEP 6 학습 포인트: WebSocket 은 끊겼다 연결되는 순간의 메시지는 잃음 → 보정 필수.
            console.log('[ws] 재연결 → 캔버스 다시 동기화');
            loadCanvas();
        },
    });

    setInterval(tickCooldownDisplay, COOLDOWN_TICK_MS);
    setInterval(pollStats, STATS_POLL_MS);
    startTicker();
}

// ─────────────────────────────────────────────────────────────────
//  하단 ticker — 접속자 수 / 1위 진영 / 칠해진 칸 수 순환
// ─────────────────────────────────────────────────────────────────

/** ticker 회전 주기 (ms). 너무 짧으면 못 읽고, 너무 길면 지루. */
const TICKER_ROTATE_MS = 3000;
/** 접속자 수 폴링 주기. */
const ONLINE_POLL_MS = 10000;
/** slide-out → 텍스트 swap → slide-in 사이 텀. CSS 의 animation 시간(0.4s)과 일치. */
const TICKER_FADE_MS = 400;

let tickerStats = null;
let tickerOnline = { online: 0, participants: 0, paints: 0 };
let tickerIndex = 0;

async function refreshTickerOnline() {
    try {
        const data = await apiGet('/api/stats/online');
        tickerOnline = {
            online: Number(data.online ?? 0),
            participants: Number(data.participants ?? 0),
            paints: Number(data.paints ?? 0),
        };
    } catch (_) { /* 무시 — 다음 폴링에서 재시도 */ }
}

/**
 * 현재 데이터로 보여줄 메시지 배열 빌드.
 * 데이터 부족하면 해당 메시지 빠짐. 빈 배열이면 ticker 표시 X.
 */
function buildTickerMessages() {
    const msgs = [];
    if (tickerOnline.online > 0) {
        msgs.push(`현재 ${tickerOnline.online}명 접속 중!`);
    }
    if (tickerStats?.factions?.length > 0) {
        const leader = tickerStats.factions.find((f) => f.rank === 1);
        if (leader && leader.pixelCount > 0) {
            msgs.push(`${leader.name} 1위!`);
        }
    }
    if (tickerOnline.participants > 0) {
        msgs.push(`지금까지 ${tickerOnline.participants}명 참여!`);
    }
    if (tickerOnline.paints > 0) {
        msgs.push(`총 ${tickerOnline.paints}번 칠해졌어요`);
    }
    return msgs;
}

function startTicker() {
    const el = document.getElementById('ticker-text');
    if (!el) return;

    const tick = () => {
        const msgs = buildTickerMessages();
        if (msgs.length === 0) return;
        const msg = msgs[tickerIndex % msgs.length];
        tickerIndex += 1;

        el.classList.remove('is-entering');
        el.classList.add('is-leaving');
        setTimeout(() => {
            el.textContent = msg;
            el.classList.remove('is-leaving');
            el.classList.add('is-entering');
        }, TICKER_FADE_MS);
    };

    // 첫 진입 — 데이터 받자마자 첫 메시지 즉시 표시 (애니메이션 없이)
    const firstShow = () => {
        const msgs = buildTickerMessages();
        if (msgs.length === 0) return;
        el.textContent = msgs[0];
        el.classList.add('is-entering');
        tickerIndex = 1;
    };

    // 데이터 fetch + 첫 표시
    refreshTickerOnline().then(() => {
        // pollStats() 가 5초마다 tickerStats 를 갱신할 거지만, 초기엔 직접 한 번 가져옴
        apiGet('/api/stats/factions').then((data) => {
            tickerStats = data;
            firstShow();
        }).catch(() => {});
    });

    setInterval(refreshTickerOnline, ONLINE_POLL_MS);
    setInterval(tick, TICKER_ROTATE_MS);
}

// ─────────────────────────────────────────────────────────────────
//  캔버스 렌더링
// ─────────────────────────────────────────────────────────────────

function fillCanvasWhite() {
    ctx.fillStyle = FACTION_COLORS[0];
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
}

/**
 * 50x50 격자선 stroke. 매 paint 후 호출되어 픽셀 위로 격자선이 다시 그려짐.
 * 비용: 102 line stroke, microsecond 단위 — 무시 가능.
 *
 * .5 오프셋 이유:
 *   Canvas 의 stroke 는 좌표의 양쪽으로 0.5px 씩 그림. 정수 좌표에 stroke 하면
 *   2px 두께로 흐리게 보임. 0.5 오프셋이면 1px 또렷한 라인.
 */
function drawGrid() {
    ctx.strokeStyle = GRID_STROKE;
    ctx.lineWidth = 1;
    ctx.beginPath();
    // 세로선 (GRID_WIDTH + 1 개) — 캔버스 높이만큼 그음
    for (let i = 0; i <= GRID_WIDTH; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(p, 0);       ctx.lineTo(p, CANVAS_H);
    }
    // 가로선 (GRID_HEIGHT + 1 개) — 캔버스 폭만큼 그음
    for (let i = 0; i <= GRID_HEIGHT; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(0, p);       ctx.lineTo(CANVAS_W, p);
    }
    ctx.stroke();
}

async function loadCanvas() {
    const data = await apiGet('/api/game/canvas');
    for (let y = 0; y < GRID_HEIGHT; y++) {
        for (let x = 0; x < GRID_WIDTH; x++) {
            const factionId = data.pixels[y][x];
            if (factionId > 0) {
                fillCell(x, y, FACTION_COLORS[factionId]);
            }
        }
    }
    drawGrid();   // 모든 픽셀 위에 격자선 한 번
}

/**
 * 셀(x,y)을 색상으로 채움.
 * 좌상단 1px 만 비워 격자선 보존 (다음 셀의 좌측·상단 grid 는 그쪽 셀이 비움) — 29×29 채움.
 * 결과: 셀 사이에 정확히 1px 격자선 하나만 보임, 추가 여백 없음. 어떤 색 위에서도 격자 또렷.
 */
function fillCell(x, y, color) {
    ctx.fillStyle = color;
    ctx.fillRect(x * CELL_PX + 1, y * CELL_PX + 1, CELL_PX - 1, CELL_PX - 1);
}

/**
 * 페인트 1개를 RAF(requestAnimationFrame) 큐에 추가.
 *
 * 왜 RAF 묶음 처리인가:
 *  - WebSocket 으로 초당 수십~수백 메시지가 와도 한 프레임(16ms) 안에 한 번만 그리도록 묶음.
 *  - 매 메시지마다 fillRect + drawGrid 호출하면 60fps 가 깨지고 화면 버벅임 발생.
 *  - 큐에 쌓다가 다음 프레임에 한 번에 fillCell 들 + 격자 한 번만 stroke → 60fps 보장.
 *  - 마감 직전 픽셀 폭주 시나리오 대비 (단일 페이지 다중 클라이언트 broadcast).
 */
const paintQueue = [];
let rafScheduled = false;

function enqueuePaintedPixel(x, y, factionId) {
    paintQueue.push({ x, y, factionId });
    if (rafScheduled) return;
    rafScheduled = true;
    requestAnimationFrame(flushPaintQueue);
}

function flushPaintQueue() {
    rafScheduled = false;
    if (paintQueue.length === 0) return;
    // 큐 비우면서 모든 셀 fillRect — 중간에 격자 stroke 안 하고 한 번에 모음
    while (paintQueue.length > 0) {
        const p = paintQueue.shift();
        fillCell(p.x, p.y, FACTION_COLORS[p.factionId] || FACTION_COLORS[0]);
    }
    // 격자선은 페인트 후 한 번만 다시 stroke. 큐 처리량 무관하게 1회 비용.
    drawGrid();
}

// ─────────────────────────────────────────────────────────────────
//  리사이즈 핸들러
// ─────────────────────────────────────────────────────────────────

/**
 * window resize 시 pixel-cursor 위치만 재계산.
 * panzoom 제거됐으므로 transform reset 없음. wrapper 는 CSS 가 알아서 fluid 리사이즈.
 */
function setupResizeHandler() {
    let resizeRaf = null;
    window.addEventListener('resize', () => {
        if (resizeRaf) return;
        resizeRaf = requestAnimationFrame(() => {
            resizeRaf = null;
            if (selected) updateCursor(selected.x, selected.y);
        });
    });
}

// ─────────────────────────────────────────────────────────────────
//  탭 핸들러 — 1탭 선택, 같은 칸 재탭 페인트
// ─────────────────────────────────────────────────────────────────

function setupTapHandler() {
    els.canvas.addEventListener('click', onCanvasClick);
    els.cancelSelect.addEventListener('click', clearSelection);
}

function onCanvasClick(e) {
    const { x, y } = clientToCell(e.clientX, e.clientY);
    if (x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT) return;

    const now = Date.now();
    const sameAsLast =
        lastTap.x === x &&
        lastTap.y === y &&
        now - lastTap.time < DOUBLE_TAP_WINDOW_MS;

    if (sameAsLast) {
        // 같은 칸 400ms 이내 재탭 → 페인트
        paintPixel(x, y);
        lastTap = { x: -1, y: -1, time: 0 };
        return;
    }

    // 단일 탭 또는 다른 칸 → 선택
    lastTap = { x, y, time: now };
    selectCell(x, y);
}

function clientToCell(clientX, clientY) {
    // canvas 의 화면상 영역 → 11×17 셀 좌표. getBoundingClientRect() 는 실제 렌더 크기 반환.
    const rect = els.canvas.getBoundingClientRect();
    const sx = GRID_WIDTH / rect.width;
    const sy = GRID_HEIGHT / rect.height;
    return {
        x: Math.floor((clientX - rect.left) * sx),
        y: Math.floor((clientY - rect.top) * sy),
    };
}

function selectCell(x, y) {
    selected = { x, y };
    updateCursor(x, y);
    els.coordValue.textContent = `(${x},${y})`;
    els.coordBar.classList.remove('hidden');
}

function clearSelection() {
    selected = null;
    els.pixelCursor.classList.remove('visible');
    els.coordBar.classList.add('hidden');
    lastTap = { x: -1, y: -1, time: 0 };
}

function updateCursor(x, y) {
    // wrapper 안에서 셀 1개 위치를 퍼센트로 매핑. CSS 가 width/height 도 같은 분모로 계산.
    els.pixelCursor.style.left = `${(x / GRID_WIDTH) * 100}%`;
    els.pixelCursor.style.top = `${(y / GRID_HEIGHT) * 100}%`;
    els.pixelCursor.classList.add('visible');
}

// ─────────────────────────────────────────────────────────────────
//  페인트 실행
// ─────────────────────────────────────────────────────────────────

async function paintPixel(x, y) {
    try {
        const res = await apiPost('/api/game/pixels', { x, y });
        // 낙관적 UI — 본인 픽셀은 응답 즉시 화면에 반영. RAF 큐로 들어가 다음 프레임에 그려짐(~16ms).
        // WebSocket 으로 같은 메시지가 또 와도 같은 색 다시 그리기 = idempotent (무해).
        enqueuePaintedPixel(res.x, res.y, res.factionId);
        cooldownEndsAt = new Date(res.cooldownEndsAt);
        tickCooldownDisplay();
        loadStats();   // 통계 즉시 갱신
        clearSelection();
    } catch (e) {
        if (e instanceof ApiError) {
            if (e.errorCode === 'COOLDOWN_ACTIVE') {
                showToast(`쿨다운 ${e.message.match(/\d+/)?.[0] ?? ''}초 남음`);
                return;
            }
            if (e.errorCode === 'GAME_NOT_ACTIVE') {
                showToast('지금은 픽셀을 칠할 수 없어요');
                return;
            }
            showToast(e.message);
        } else {
            showToast('네트워크 오류');
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  통계 (전체 순위 모달)
// ─────────────────────────────────────────────────────────────────

/**
 * 1 → 1st, 2 → 2nd, 3 → 3rd, 그 외 → Nth.
 * 한국어 "위" 는 픽셀 폰트(DOSGothic/Press Start 2P)에 한글 글리프 없어 fallback 됨 — 영어 ordinal 로 일관된 픽셀 룩.
 */
function ordinal(n) {
    if (n === 1) return '1st';
    if (n === 2) return '2nd';
    if (n === 3) return '3rd';
    return `${n}th`;
}

async function loadStats() {
    try {
        const data = await apiGet('/api/stats/factions');
        renderStats(data);
    } catch (e) {
        console.warn('[stats] 로드 실패', e);
    }
}

function renderStats(data) {
    // ── 상단 점유율 막대 ──────────────────────────────────────────
    // 큰 진영부터 왼쪽으로 정렬. 0% 진영은 숨김. 마지막에 흰 픽셀(미점령).
    renderFactionBar(data);

    // ── 모달 리스트 (탭 시 열림) ─────────────────────────────────
    els.statsList.innerHTML = data.factions
        .map((f) => {
            const isMine = f.id === me.faction.id ? ' is-mine' : '';
            return `
                <li class="stat-item${isMine}" style="--faction-color: ${f.colorHex}">
                    <span class="stat-item__dot"></span>
                    <span class="stat-item__name">${f.name}</span>
                    <span class="stat-item__rank font-pixel">${ordinal(f.rank)}</span>
                    <span class="stat-item__pct font-pixel">${f.percentage.toFixed(1)}%</span>
                </li>
            `;
        })
        .join('');

    els.statsMeta.textContent =
        `칠해진 픽셀 ${data.totalPixels - data.whitePixels} / ${data.totalPixels}` +
        ` · ${new Date(data.calculatedAt).toLocaleTimeString('ko-KR')} 기준`;
}

/**
 * 진영 점유율 막대 렌더 — flex 컨테이너에 세그먼트 div 채움.
 * 정렬: pixelCount 큰 진영부터 왼쪽. 마지막 세그먼트는 흰 픽셀. 0% 진영은 생략.
 * 막대 자체가 모달 트리거이므로 세그먼트별 데이터는 따로 안 붙임.
 */
function renderFactionBar(data) {
    const bar = els.factionBar;
    bar.innerHTML = '';
    const total = data.totalPixels;
    if (total <= 0) return;

    const sorted = [...data.factions].sort((a, b) => b.pixelCount - a.pixelCount);
    for (const f of sorted) {
        if (f.pixelCount === 0) continue;
        const seg = document.createElement('div');
        seg.className = 'faction-bar__segment';
        seg.style.flexBasis = `${(f.pixelCount / total) * 100}%`;
        seg.style.background = f.colorHex;
        bar.appendChild(seg);
    }
    if (data.whitePixels > 0) {
        const white = document.createElement('div');
        white.className = 'faction-bar__segment';
        white.style.flexBasis = `${(data.whitePixels / total) * 100}%`;
        white.style.background = 'var(--white-canvas)';
        bar.appendChild(white);
    }
}

// ─────────────────────────────────────────────────────────────────
//  쿨다운
// ─────────────────────────────────────────────────────────────────

async function loadInitialCooldown() {
    try {
        const data = await apiGet('/api/game/cooldown');
        if (data.remainingSec > 0) {
            cooldownEndsAt = new Date(data.endsAt);
        }
    } catch (e) {
        console.warn('[cooldown] 로드 실패', e);
    }
    tickCooldownDisplay();
}

function tickCooldownDisplay() {
    if (!cooldownEndsAt) {
        els.cooldown.textContent = 'READY';
        els.cooldown.classList.remove('active');
        els.cooldown.classList.add('ready');
        return;
    }
    const ms = cooldownEndsAt.getTime() - Date.now();
    if (ms <= 0) {
        cooldownEndsAt = null;
        els.cooldown.textContent = 'READY';
        els.cooldown.classList.remove('active');
        els.cooldown.classList.add('ready');
        return;
    }
    const totalSec = Math.ceil(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    els.cooldown.textContent = `${m}:${String(s).padStart(2, '0')}`;
    els.cooldown.classList.add('active');
    els.cooldown.classList.remove('ready');
}

// ─────────────────────────────────────────────────────────────────
//  통계 폴링 (status 는 WebSocket 으로 받음)
// ─────────────────────────────────────────────────────────────────

/**
 * 진영 통계만 폴링.
 *
 * 왜 status 는 WebSocket, stats 는 폴링?
 *  - status: 전환 빈도 매우 낮음(하루 4-5회), 즉시성 중요 → WebSocket 적합
 *  - stats:  픽셀마다 바뀌어 메시지 부담 크고, 5초 늦어도 무방 → 폴링 + 1초 캐시 충분
 */
async function pollStats() {
    try {
        const stats = await apiGet('/api/stats/factions');
        tickerStats = stats;  // ticker 도 같은 데이터 사용
        renderStats(stats);
    } catch (e) {
        console.warn('[poll] stats 실패', e);
    }
}

// ─────────────────────────────────────────────────────────────────
//  모달 핸들러
// ─────────────────────────────────────────────────────────────────

function setupModalHandlers() {
    // 상단 진영 막대가 곧 모달 트리거.
    setupFactionBarInteraction();

    els.closeStats.addEventListener('click', () => {
        els.statsModal.classList.add('hidden');
    });
    els.statsModal.addEventListener('click', (e) => {
        if (e.target === els.statsModal) els.statsModal.classList.add('hidden');
    });

    // 마이페이지(내 픽셀 발자취) — status-bar 의 내 진영 탭 시 모달 열림
    els.myFactionBtn?.addEventListener('click', openPaintsModal);
    els.closePaints?.addEventListener('click', () => els.paintsModal.classList.add('hidden'));
    els.paintsModal?.addEventListener('click', (e) => {
        if (e.target === els.paintsModal) els.paintsModal.classList.add('hidden');
    });

    // 온보딩 — 첫 진입 시 1회만 표시. 5장 슬라이드, 다음/건너뛰기/스와이프.
    setupOnboarding();
}

// ─────────────────────────────────────────────────────────────────
//  마이페이지 — 본인 페인트 이력
// ─────────────────────────────────────────────────────────────────

async function openPaintsModal() {
    els.paintsModal.classList.remove('hidden');
    els.paintsList.innerHTML = '<li class="paints-empty">로딩 중…</li>';
    els.paintsMeta.textContent = '';
    try {
        const data = await apiGet('/api/users/me/paints');
        renderPaints(data);
    } catch (e) {
        console.warn('[paints] 로드 실패', e);
        els.paintsList.innerHTML = '<li class="paints-empty">불러올 수 없습니다.</li>';
    }
}

function renderPaints(data) {
    // 요약 카드 — 진영색 좌측 라인 + 진영명 + 총 페인트 수
    const color = data.factionColorHex || 'var(--text-tertiary)';
    els.paintsSummary.style.setProperty('--faction-color', color);
    els.paintsSummary.innerHTML = `
        <span class="faction-color-dot" style="background: ${color}" aria-hidden="true"></span>
        <span class="paints-summary__faction font-pixel">${data.factionName ?? '미선택'}</span>
        <span class="paints-summary__count">총 ${data.totalPaints}개</span>
    `;

    if (!data.paints || data.paints.length === 0) {
        els.paintsList.innerHTML = '<li class="paints-empty">아직 칠한 픽셀이 없어요.<br>한 칸 칠해보세요!</li>';
        els.paintsMeta.textContent = '';
        return;
    }

    els.paintsList.innerHTML = data.paints.map((p) => {
        const dt = new Date(p.paintedAt);
        const time = dt.toLocaleTimeString('ko-KR', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
        });
        const date = `${dt.getMonth() + 1}/${dt.getDate()}`;
        const cls = p.alive ? 'is-alive' : 'is-dead';
        // 살아있음 ✓ / 덮임 ✗ — 픽셀 폰트의 화살표 대용
        const icon = p.alive ? '✓' : '✗';
        return `
            <li class="paint-item">
                <span class="paint-item__status ${cls}" aria-label="${p.alive ? '살아있음' : '덮였음'}">${icon}</span>
                <span class="paint-item__coord">(${p.x},${p.y})</span>
                <span class="paint-item__time">${date} ${time}</span>
            </li>
        `;
    }).join('');

    const aliveCount = data.paints.filter((p) => p.alive).length;
    els.paintsMeta.textContent =
        `최근 ${data.paints.length}개 중 ${aliveCount}개가 아직 내 색입니다`;
}

/**
 * 온보딩 슬라이드 컨트롤러.
 *  - 도트 표시 / 다음 버튼 / 건너뛰기 / 스와이프 모두 지원
 *  - 마지막 슬라이드에선 CTA 라벨이 "시작!" 으로 변경
 *  - 종료 시 localStorage 'dotwars_tutorial_seen' = '1' 설정
 *
 * Private/Incognito 모드에서 localStorage 차단 시 매번 보임 (수용 가능).
 */
function setupOnboarding() {
    const root = document.getElementById('onboarding');
    if (!root) return;

    const slides = document.getElementById('onboarding-slides');
    const dots = document.querySelectorAll('#onboarding-dots .onboarding__dot');
    const cta = document.getElementById('onboarding-cta');

    const TOTAL = dots.length;
    let index = 0;

    const update = () => {
        slides.style.transform = `translateX(-${index * 100}%)`;
        dots.forEach((d, i) => d.classList.toggle('is-active', i === index));
        cta.textContent = (index === TOTAL - 1) ? '시작!' : '다음 →';
    };

    const finish = () => {
        root.classList.add('hidden');
        try { localStorage.setItem('dotwars_tutorial_seen', '1'); } catch (_) {}
    };

    cta.addEventListener('click', () => {
        if (index < TOTAL - 1) { index += 1; update(); }
        else { finish(); }
    });

    // 도트 직접 클릭 — 해당 슬라이드로 점프 (다른 앱 패턴)
    dots.forEach((dot, i) => {
        dot.addEventListener('click', () => {
            index = i;
            update();
        });
    });

    // ── 가로 스와이프 (touch) ──────────────────────────────────────
    const SWIPE_THRESHOLD_PX = 50;
    let touchStartX = null;
    root.addEventListener('touchstart', (e) => {
        if (e.touches.length !== 1) return;
        touchStartX = e.touches[0].clientX;
    }, { passive: true });
    root.addEventListener('touchend', (e) => {
        if (touchStartX === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX;
        touchStartX = null;
        if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return;
        if (dx < 0 && index < TOTAL - 1) { index += 1; update(); }
        else if (dx > 0 && index > 0)    { index -= 1; update(); }
    }, { passive: true });

    update();
}

/**
 * 온보딩 — 첫 진입 시 1회만 표시.
 * localStorage 'dotwars_tutorial_seen' === '1' 이면 스킵.
 */
function showTutorialIfFirstTime() {
    let seen = false;
    try { seen = localStorage.getItem('dotwars_tutorial_seen') === '1'; } catch (_) {}
    if (seen) return;
    document.getElementById('onboarding')?.classList.remove('hidden');
}

/**
 * 진영 막대 인터랙션 — 단순. 탭/클릭/Enter/Space → 전체 순위 모달.
 * 이전엔 짧은탭/꾹누름 구분했으나 짧은탭이 일부 폰에서 작동 안 함 + 진영별 툴팁이 활용도 낮음 → 단순화 (2026-05-19).
 */
function setupFactionBarInteraction() {
    const openModal = () => {
        loadStats();
        els.statsModal.classList.remove('hidden');
    };
    els.factionBar.addEventListener('click', openModal);
    els.factionBar.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openModal();
        }
    });
}

// ─────────────────────────────────────────────────────────────────
//  Toast
// ─────────────────────────────────────────────────────────────────

function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
}
