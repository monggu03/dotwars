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

// ── 상수 ────────────────────────────────────────────────────────
const GRID_SIZE = 50;           // 50x50 셀
const CELL_PX = 10;             // 셀당 캔버스 픽셀 (해상도 500x500)
const CANVAS_PX = GRID_SIZE * CELL_PX;
const GRID_STROKE = 'rgba(0, 0, 0, 0.10)';   // 옅은 회색 격자선
const POLL_INTERVAL_MS = 3000;
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

    await Promise.all([
        loadCanvas(),
        loadInitialCooldown(),
        loadStats(),
    ]);

    setupPanzoom();
    setupTapHandler();
    setupModalHandlers();
    setupResizeHandler();

    setInterval(tickCooldownDisplay, COOLDOWN_TICK_MS);
    setInterval(poll, POLL_INTERVAL_MS);
}

// ─────────────────────────────────────────────────────────────────
//  캔버스 렌더링
// ─────────────────────────────────────────────────────────────────

function fillCanvasWhite() {
    ctx.fillStyle = FACTION_COLORS[0];
    ctx.fillRect(0, 0, CANVAS_PX, CANVAS_PX);
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
    for (let i = 0; i <= GRID_SIZE; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(p, 0);       ctx.lineTo(p, CANVAS_PX);
        ctx.moveTo(0, p);       ctx.lineTo(CANVAS_PX, p);
    }
    ctx.stroke();
}

async function loadCanvas() {
    const data = await apiGet('/api/game/canvas');
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
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

/** 페인트 1개 + 격자 위에 한 번 더. */
function drawPaintedPixel(x, y, factionId) {
    fillCell(x, y, FACTION_COLORS[factionId] || FACTION_COLORS[0]);
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
    if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) return;

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
    const sx = GRID_SIZE / rect.width;
    const sy = GRID_SIZE / rect.height;
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
    // wrapper 의 2% × 2% = 1 cell. left/top 도 퍼센트로 매핑.
    els.pixelCursor.style.left = `${(x / GRID_SIZE) * 100}%`;
    els.pixelCursor.style.top = `${(y / GRID_SIZE) * 100}%`;
    els.pixelCursor.classList.add('visible');
}

// ─────────────────────────────────────────────────────────────────
//  페인트 실행
// ─────────────────────────────────────────────────────────────────

async function paintPixel(x, y) {
    try {
        const res = await apiPost('/api/game/pixels', { x, y });
        drawPaintedPixel(res.x, res.y, res.factionId);   // 낙관적 UI
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
//  폴링
// ─────────────────────────────────────────────────────────────────

async function poll() {
    try {
        const [status, stats] = await Promise.all([
            apiGet('/api/game/status'),
            apiGet('/api/stats/factions'),
        ]);
        renderStats(stats);
        if (status.status === 'ENDED') {
            location.replace('/game-end.html');
        }
    } catch (e) {
        console.warn('[poll] 실패', e);
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
