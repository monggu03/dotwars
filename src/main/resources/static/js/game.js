/*
 * game.js — r/place 식 풀스크린 캔버스 게임.
 *
 * 변경 (2026-05-16):
 *  - canvas 해상도 50→500 (셀당 10x10). 매 paint 후 격자선 stroke.
 *  - 확대 시트 제거. 1탭 → pixel-cursor + coord-bar 표시.
 *  - 같은 칸 400ms 이내 재탭 → 페인트 실행.
 *  - window resize 시 panzoom reset → 정중앙 정렬 안정.
 */

import { apiGet, apiPost, ApiError } from './api.js';
import { requireLoginWithDepartment } from './auth-guard.js';
import { connectWebSocket } from './websocket.js';

// ── 상수 ────────────────────────────────────────────────────────
// 캔버스 그리드 — 모바일 9:16 비율. application.yml 의 game.canvas.* 와 일치 필요.
const GRID_WIDTH = 18;
const GRID_HEIGHT = 32;
const CELL_PX = 20;             // 셀당 캔버스 픽셀 (해상도 360x640)
const CANVAS_W = GRID_WIDTH * CELL_PX;
const CANVAS_H = GRID_HEIGHT * CELL_PX;
const GRID_STROKE = 'rgba(0, 0, 0, 0.10)';   // 옅은 회색 격자선
// 통계는 WebSocket 으로 안 보내고 폴링 유지 — 부하 크고 실시간성 덜 중요.
// 3초 → 5초로 늘림 (WebSocket 으로 picks 즉시 갱신되니까 굳이 짧을 필요 없음).
const STATS_POLL_MS = 5000;
const COOLDOWN_TICK_MS = 1000;
const DOUBLE_TAP_WINDOW_MS = 400;   // 같은 칸 재탭으로 인정하는 시간 창

const FACTION_COLORS = {
    0: '#FFFFFF',
    1: '#FF7F0E',
    2: '#D62728',
    3: '#2CA02C',
    4: '#1F77B4',
    5: '#9467BD',
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
    myRank: $('my-rank'),
    cooldown: $('cooldown-timer'),
    openStats: $('open-stats'),
    statsModal: $('stats-modal'),
    closeStats: $('close-stats'),
    statsList: $('stats-list'),
    statsMeta: $('stats-meta'),
    banner: $('game-banner'),
    bannerLabel: $('banner-label'),
    bannerCountdown: $('banner-countdown'),
};

// ── 모듈 상태 ────────────────────────────────────────────────────
let me = null;
let ctx = null;
let panzoomInstance = null;
let cooldownEndsAt = null;
// 더블탭 감지 — 마지막 1탭의 좌표와 시각
let lastTap = { x: -1, y: -1, time: 0 };
// 현재 선택된 칸 (1탭으로 선택, 페인트하거나 다른 칸 클릭하거나 취소까지 유지)
let selected = null;   // { x, y } | null
// 휴식 배너의 카운트다운 목표 시각. ACTIVE 상태에선 null.
let bannerNextStartsAt = null;

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

    const status = await apiGet('/api/game/status').catch(() => null);
    if (status?.status === 'ENDED') {
        location.replace('/game-end.html');
        return;
    }
    // FROZEN/SCHEDULED 면 휴식 배너 노출. ACTIVE 면 hidden 유지.
    updateBanner(status);

    await Promise.all([
        loadCanvas(),
        loadInitialCooldown(),
        loadStats(),
    ]);

    setupPanzoom();
    setupTapHandler();
    setupModalHandlers();
    setupResizeHandler();

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
            // WebSocket 메시지는 status만 담겨 올 수 있으므로 nextSession 까지 받기 위해 status API 재호출.
            // 페인트 API 는 서버가 이미 GAME_NOT_ACTIVE 로 막아주니 UI 잠금은 안내(배너+토스트) 만으로 충분.
            apiGet('/api/game/status').catch(() => null).then((s) => {
                if (s) updateBanner(s);
            });
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
    setInterval(tickBanner, COOLDOWN_TICK_MS);   // 휴식 배너 카운트다운 갱신
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

/** 셀(x,y)을 색상으로 채움. 격자는 별도로 다시 그려야 함. */
function fillCell(x, y, color) {
    ctx.fillStyle = color;
    ctx.fillRect(x * CELL_PX, y * CELL_PX, CELL_PX, CELL_PX);
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
//  panzoom 설정 + 리사이즈
// ─────────────────────────────────────────────────────────────────

function setupPanzoom() {
    panzoomInstance = window.Panzoom(els.wrapper, {
        minScale: 1,
        maxScale: 16,
        contain: 'outside',
        cursor: 'crosshair',
        animate: false,
    });

    els.stage.addEventListener('wheel', (e) => {
        panzoomInstance.zoomWithWheel(e);
    });
}

/**
 * window resize 시 panzoom 의 transform 을 초기 상태로 reset → 캔버스가 정중앙 유지.
 * 사용자 보고: "윈도우 조절할 때마다 쏠려있다" → resize 시 자동 정렬 회복.
 *
 * RAF 디바운스:
 *   리사이즈 중 매 프레임 reset 호출이 누적되는 걸 막음.
 */
function setupResizeHandler() {
    let resizeRaf = null;
    window.addEventListener('resize', () => {
        if (resizeRaf) return;
        resizeRaf = requestAnimationFrame(() => {
            resizeRaf = null;
            panzoomInstance?.reset({ animate: false });
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
    // canvas 의 화면상 영역 → 50x50 셀 좌표.
    // panzoom 의 transform 도 getBoundingClientRect() 에 반영되므로 비례 계산만으로 정확.
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

async function loadStats() {
    try {
        const data = await apiGet('/api/stats/factions');
        renderStats(data);
    } catch (e) {
        console.warn('[stats] 로드 실패', e);
    }
}

function renderStats(data) {
    const mine = data.factions.find((f) => f.id === me.faction.id);
    if (mine) {
        els.myRank.textContent = `${mine.rank}위`;
    }

    els.statsList.innerHTML = data.factions
        .map((f) => {
            const isMine = f.id === me.faction.id ? ' is-mine' : '';
            return `
                <li class="stat-item${isMine}" style="--faction-color: ${f.colorHex}">
                    <span class="stat-item__dot"></span>
                    <span class="stat-item__name">${f.name}</span>
                    <span class="stat-item__rank font-pixel">${f.rank}위</span>
                    <span class="stat-item__pct font-pixel">${f.percentage.toFixed(1)}%</span>
                </li>
            `;
        })
        .join('');

    els.statsMeta.textContent =
        `칠해진 픽셀 ${data.totalPixels - data.whitePixels} / ${data.totalPixels}` +
        ` · ${new Date(data.calculatedAt).toLocaleTimeString('ko-KR')} 기준`;
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
//  휴식 시간 배너 (FROZEN / SCHEDULED)
// ─────────────────────────────────────────────────────────────────

/**
 * /api/game/status 응답 한 번을 받아 배너 표시 여부 + 카운트다운 목표 시각 갱신.
 *
 * 호출 시점:
 *  - init() 초기 1회
 *  - WebSocket onGameStatusChanged (페이지 새로고침 없이 전환 받음)
 *  - tickBanner() 가 카운트다운 0 도달 시 재호출 (서버 시계가 진실원천)
 */
function updateBanner(status) {
    if (!status || status.status === 'ACTIVE' || status.status === 'ENDED') {
        // 게임 진행 중 → 배너 숨김. ENDED 는 다른 로직이 game-end.html 로 보냄.
        bannerNextStartsAt = null;
        els.banner.classList.add('hidden');
        return;
    }
    // FROZEN 또는 SCHEDULED — 다음 세션 시작 시각으로 카운트다운
    const next = status.nextSession;
    if (next?.startsAt) {
        bannerNextStartsAt = new Date(next.startsAt);
        // "Day 1 · 16:00 시작" 형식. KST 로컬타임으로 자동 변환됨.
        const hm = bannerNextStartsAt.toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
        });
        els.bannerLabel.textContent = `Day ${next.dayNumber} · ${hm} 시작`;
    } else {
        // 다음 세션 정보 없음 (드문 케이스 — 일정이 모두 끝났는데 ENDED 전환 직전)
        bannerNextStartsAt = null;
        els.bannerLabel.textContent = '휴식 시간';
        els.bannerCountdown.textContent = '';
    }
    els.banner.classList.remove('hidden');
    tickBanner();   // 즉시 1회 갱신 (interval 첫 틱까지 1초 기다리지 않게)
}

/**
 * 1초마다 호출되어 남은 시간 텍스트 갱신.
 *
 * 카운트다운 0 도달 시 status 재조회 — WebSocket 으로 ACTIVE 가 늦게 와도
 * 클라이언트에서 능동적으로 상태 폴링 한 번 해서 UI 막힘 방지.
 */
function tickBanner() {
    if (!bannerNextStartsAt) return;
    const ms = bannerNextStartsAt.getTime() - Date.now();
    if (ms <= 0) {
        els.bannerCountdown.textContent = '시작합니다…';
        bannerNextStartsAt = null;
        // 서버 상태 다시 확인. WebSocket 메시지가 곧 ACTIVE 를 줄 텐데 그 전에 능동 보정.
        apiGet('/api/game/status').catch(() => null).then((s) => {
            if (s) updateBanner(s);
        });
        return;
    }
    const totalSec = Math.ceil(ms / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    // 픽셀 폰트 + monospace 라 자리수 변화에도 흔들림 없음
    els.bannerCountdown.textContent = h > 0
        ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')} 남음`
        : `${m}:${String(s).padStart(2, '0')} 남음`;
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
        renderStats(stats);
    } catch (e) {
        console.warn('[poll] stats 실패', e);
    }
}

// ─────────────────────────────────────────────────────────────────
//  모달 핸들러
// ─────────────────────────────────────────────────────────────────

function setupModalHandlers() {
    els.openStats.addEventListener('click', () => {
        loadStats();
        els.statsModal.classList.remove('hidden');
    });
    els.closeStats.addEventListener('click', () => {
        els.statsModal.classList.add('hidden');
    });
    els.statsModal.addEventListener('click', (e) => {
        if (e.target === els.statsModal) els.statsModal.classList.add('hidden');
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
