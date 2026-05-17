/*
 * game-end.js — 게임 종료 결과 화면.
 *
 * 동시 호출:
 *  - GET /api/stats/factions  → 진영별 최종 카운트 + 순위 + 비율
 *  - GET /api/game/canvas     → 최종 캔버스 픽셀 상태 (snapshot)
 *  - GET /api/users/me        → 본인 진영 (로그인 사용자만, 401 OK)
 *
 * 캔버스:
 *  - 500x500 해상도 (game.js 와 동일 패턴)
 *  - 셀당 10x10 + 격자선
 *  - toDataURL('image/png') 로 다운로드 가능
 */

import { apiGet, ApiError } from './api.js';

const GRID_SIZE = 50;
const CELL_PX = 10;
const CANVAS_PX = GRID_SIZE * CELL_PX;
const GRID_STROKE = 'rgba(0, 0, 0, 0.10)';

const FACTION_COLORS = {
    0: '#FFFFFF',
    1: '#FF7F0E',
    2: '#D62728',
    3: '#2CA02C',
    4: '#1F77B4',
    5: '#9467BD',
};

const $ = (id) => document.getElementById(id);
const els = {
    canvas: $('end-canvas'),
    caption: $('canvas-caption'),
    winnerCard: $('winner'),
    winnerName: $('winner-name'),
    winnerPct: $('winner-pct'),
    winnerPixels: $('winner-pixels'),
    myCard: $('my-card'),
    myFactionName: $('my-faction-name'),
    myRank: $('my-rank'),
    ranking: $('ranking'),
    downloadBtn: $('download-canvas'),
    backHome: $('back-home'),
};

init();

async function init() {
    // 본인 정보는 로그인 안 했어도 결과 화면 열람 가능 → 실패 OK
    const [stats, canvas, me] = await Promise.all([
        apiGet('/api/stats/factions').catch(() => null),
        apiGet('/api/game/canvas').catch(() => null),
        apiGet('/api/users/me').catch((e) => {
            if (e instanceof ApiError && e.status === 401) return null;
            throw e;
        }),
    ]);

    if (canvas) drawCanvas(canvas);
    if (stats) renderStats(stats, me?.faction?.id);
    if (me?.faction) renderMyFaction(me, stats);

    bindEvents();
}

// ── 캔버스 렌더 + 격자 ────────────────────────────────────────

function drawCanvas(data) {
    const ctx = els.canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;

    // 흰 배경
    ctx.fillStyle = FACTION_COLORS[0];
    ctx.fillRect(0, 0, CANVAS_PX, CANVAS_PX);

    // 픽셀 셀 채우기
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            const fid = data.pixels[y][x];
            if (fid > 0) {
                ctx.fillStyle = FACTION_COLORS[fid] || FACTION_COLORS[0];
                ctx.fillRect(x * CELL_PX, y * CELL_PX, CELL_PX, CELL_PX);
            }
        }
    }

    // 격자선 (game.js 와 동일 패턴 — 0.5 오프셋으로 또렷한 1px 라인)
    ctx.strokeStyle = GRID_STROKE;
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let i = 0; i <= GRID_SIZE; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(p, 0);       ctx.lineTo(p, CANVAS_PX);
        ctx.moveTo(0, p);       ctx.lineTo(CANVAS_PX, p);
    }
    ctx.stroke();

    const ts = new Date(data.snapshotAt).toLocaleString('ko-KR');
    els.caption.textContent = `최종 상태 · ${ts}`;
}

// ── 통계 + 순위 + 비율 바 ─────────────────────────────────────

function renderStats(data, myFactionId) {
    const ranked = data.factions;   // rank 순 정렬됨
    if (ranked.length === 0) return;

    // 우승 진영 카드
    const winner = ranked[0];
    els.winnerCard.style.setProperty('--faction-color', winner.colorHex);
    els.winnerName.textContent = winner.name;
    els.winnerPct.textContent = `${winner.percentage.toFixed(1)}%`;
    els.winnerPixels.textContent = `${winner.pixelCount.toLocaleString()}픽셀`;

    // 진영별 순위 + 비율 바
    els.ranking.innerHTML = ranked.map((f) => {
        const mine = myFactionId && f.id === myFactionId ? ' is-mine' : '';
        return `
            <li class="end-ranking__row${mine}"
                style="--row-color: ${f.colorHex}; --bar-pct: ${f.percentage}%;">
                <span class="end-ranking__rank">${f.rank}위</span>
                <span class="end-ranking__dot"></span>
                <div class="end-ranking__main">
                    <span class="end-ranking__name">${f.name}</span>
                    <span class="end-ranking__bar"></span>
                </div>
                <span class="end-ranking__pct">${f.percentage.toFixed(1)}%</span>
            </li>
        `;
    }).join('');
}

// ── 본인 진영 강조 카드 ──────────────────────────────────────

function renderMyFaction(me, stats) {
    if (!me?.faction || !stats) return;
    const myStat = stats.factions.find((f) => f.id === me.faction.id);
    if (!myStat) return;

    els.myCard.style.setProperty('--faction-color', me.faction.colorHex);
    els.myFactionName.textContent = me.faction.name;
    els.myRank.textContent = `${myStat.rank}위 · ${myStat.percentage.toFixed(1)}%`;
    els.myCard.classList.remove('hidden');
}

// ── 액션 ─────────────────────────────────────────────────────

function bindEvents() {
    els.downloadBtn?.addEventListener('click', () => {
        // canvas → PNG dataURL → <a download> 트릭으로 저장
        const link = document.createElement('a');
        link.download = `dotwars-final-${Date.now()}.png`;
        link.href = els.canvas.toDataURL('image/png');
        link.click();
    });

    els.backHome?.addEventListener('click', () => {
        location.replace('/');
    });
}
