/*
 * game-end.js — 게임 종료 결과 화면.
 *
 * 동시 호출:
 *  - GET /api/stats/factions  → 진영별 최종 카운트 + 순위 + 비율
 *  - GET /api/game/canvas     → 최종 캔버스 픽셀 상태 (snapshot)
 *  - GET /api/users/me        → 본인 진영 (로그인 사용자만, 401 OK)
 *
 * 캔버스:
 *  - 330×510 해상도 (game.js 와 동일 패턴, 11×17)
 *  - 셀당 30×30 + 격자선
 *  - toDataURL('image/png') 로 다운로드 가능
 */

import { apiGet, ApiError } from './api.js';

// 11:17 비율 — game.js 와 동일. application.yml game.canvas.* 와 일치.
const GRID_WIDTH = 11;
const GRID_HEIGHT = 17;
const CELL_PX = 30;
const CANVAS_W = GRID_WIDTH * CELL_PX;
const CANVAS_H = GRID_HEIGHT * CELL_PX;
const GRID_STROKE = 'rgba(0, 0, 0, 0.35)';   // game.js 와 동일

// Game Vibrant 팔레트 (2026-05-19) — DB factions.color_hex / tokens.css 와 반드시 동기화.
const FACTION_COLORS = {
    0: '#FFFFFF',
    1: '#FFA040',   // 인문 주황
    2: '#F04545',   // 사회 빨강
    3: '#43D043',   // 자연 초록
    4: '#3DA8DE',   // 공학 파랑
    5: '#B08AE0',   // 예술 보라
};

const $ = (id) => document.getElementById(id);
const els = {
    canvas: $('end-canvas'),
    caption: $('canvas-caption'),
    winnerCard: $('winner'),
    winnerName: $('winner-name'),
    winnerDepartments: $('winner-departments'),
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
    // departments: 우승 진영의 소속 단과대 목록 표시용
    const [stats, canvas, me, departments] = await Promise.all([
        apiGet('/api/stats/factions').catch(() => null),
        apiGet('/api/game/canvas').catch(() => null),
        apiGet('/api/users/me').catch((e) => {
            if (e instanceof ApiError && e.status === 401) return null;
            throw e;
        }),
        apiGet('/api/departments').catch(() => null),
    ]);

    if (canvas) drawCanvas(canvas);
    if (stats) renderStats(stats, me?.faction?.id, departments);
    if (me?.faction) renderMyFaction(me, stats);

    bindEvents();
}

// ── 캔버스 렌더 + 격자 ────────────────────────────────────────

function drawCanvas(data) {
    const ctx = els.canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;

    // 흰 배경
    ctx.fillStyle = FACTION_COLORS[0];
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // 픽셀 셀 채우기
    for (let y = 0; y < GRID_HEIGHT; y++) {
        for (let x = 0; x < GRID_WIDTH; x++) {
            const fid = data.pixels[y][x];
            if (fid > 0) {
                ctx.fillStyle = FACTION_COLORS[fid] || FACTION_COLORS[0];
                // game.js fillCell 과 동일 — 좌상단 1px 만 비워 격자 보존, 셀 사이 여백 없음.
                ctx.fillRect(x * CELL_PX + 1, y * CELL_PX + 1, CELL_PX - 1, CELL_PX - 1);
            }
        }
    }

    // 격자선 (game.js 와 동일 패턴 — 0.5 오프셋으로 또렷한 1px 라인)
    ctx.strokeStyle = GRID_STROKE;
    ctx.lineWidth = 1;
    ctx.beginPath();
    // 세로선
    for (let i = 0; i <= GRID_WIDTH; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(p, 0);       ctx.lineTo(p, CANVAS_H);
    }
    // 가로선
    for (let i = 0; i <= GRID_HEIGHT; i++) {
        const p = i * CELL_PX + 0.5;
        ctx.moveTo(0, p);       ctx.lineTo(CANVAS_W, p);
    }
    ctx.stroke();

    const ts = new Date(data.snapshotAt).toLocaleString('ko-KR');
    els.caption.textContent = `최종 상태 · ${ts}`;
}

// ── 통계 + 순위 + 비율 바 ─────────────────────────────────────

/** game.js ordinal() 과 동일 — 픽셀 폰트와 호환되는 영어 ordinal. */
function ordinal(n) {
    if (n === 1) return '1st';
    if (n === 2) return '2nd';
    if (n === 3) return '3rd';
    return `${n}th`;
}

/**
 * 단과대 목록을 " · " 로 연결. 4개 이상이면 가운데서 <br> 줄바꿈 (한 줄이 너무 길어지는 것 방지).
 * 현재 4개인 진영은 사회진영뿐 → 2+2 로 나뉨. 반환값은 HTML (innerHTML 으로 삽입).
 */
function formatDepartments(names) {
    if (names.length >= 4) {
        const mid = Math.ceil(names.length / 2);
        return names.slice(0, mid).join(' · ') + '<br>' + names.slice(mid).join(' · ');
    }
    return names.join(' · ');
}

function renderStats(data, myFactionId, departments) {
    const ranked = data.factions;   // rank 순 정렬됨
    if (ranked.length === 0) return;

    // 우승 진영 카드
    const winner = ranked[0];
    els.winnerCard.style.setProperty('--faction-color', winner.colorHex);
    els.winnerName.textContent = winner.name;
    els.winnerPct.textContent = `${winner.percentage.toFixed(1)}%`;
    els.winnerPixels.textContent = `${winner.pixelCount.toLocaleString()}픽셀`;

    // 우승 진영 소속 단과대 — /api/departments 에서 winner.id 매칭
    if (departments?.factions) {
        const wf = departments.factions.find((f) => f.id === winner.id);
        if (wf?.departments?.length) {
            els.winnerDepartments.innerHTML = formatDepartments(wf.departments.map((d) => d.name));
        }
    }

    // 진영별 → 소속 단과대 lookup (id → "불교대학 · 문과대학 · ...")
    const deptByFaction = {};
    if (departments?.factions) {
        for (const wf of departments.factions) {
            deptByFaction[wf.id] = formatDepartments((wf.departments ?? []).map((d) => d.name));
        }
    }

    // 진영별 순위 + 비율 바
    els.ranking.innerHTML = ranked.map((f) => {
        const mine = myFactionId && f.id === myFactionId ? ' is-mine' : '';
        const depts = deptByFaction[f.id] || '';
        const deptLine = depts ? `<span class="end-ranking__departments">${depts}</span>` : '';
        return `
            <li class="end-ranking__row${mine}"
                style="--row-color: ${f.colorHex}; --bar-pct: ${f.percentage}%;">
                <span class="end-ranking__rank">${ordinal(f.rank)}</span>
                <span class="end-ranking__dot"></span>
                <div class="end-ranking__main">
                    <span class="end-ranking__name">${f.name}</span>
                    ${deptLine}
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
    els.myRank.textContent = `${ordinal(myStat.rank)} · ${myStat.percentage.toFixed(1)}%`;
    els.myCard.classList.remove('hidden');
}

// ── 액션 ─────────────────────────────────────────────────────

function bindEvents() {
    els.downloadBtn?.addEventListener('click', downloadResultImage);

    els.backHome?.addEventListener('click', () => {
        location.replace('/');
    });
}

/**
 * 결과 화면 전체를 PNG 로 저장.
 *  - html2canvas 로 .end-screen 전체(캔버스 + 우승카드 + 순위 등) 캡쳐
 *  - 캡쳐 동안 액션 버튼은 잠깐 숨김 (저장 이미지에 버튼 안 나오게)
 *  - scale: 2 로 retina 화질
 */
async function downloadResultImage() {
    const target = document.querySelector('.end-screen');
    const actions = document.querySelector('.end-actions');
    if (!target || typeof window.html2canvas !== 'function') {
        // html2canvas 로드 실패 시 — 캔버스만이라도 저장 (폴백)
        const link = document.createElement('a');
        link.download = `dotwars-final-${Date.now()}.png`;
        link.href = els.canvas.toDataURL('image/png');
        link.click();
        return;
    }

    const prevLabel = els.downloadBtn.textContent;
    els.downloadBtn.disabled = true;
    els.downloadBtn.textContent = '이미지 생성 중…';
    if (actions) actions.style.visibility = 'hidden';

    try {
        const bg = getComputedStyle(document.body).backgroundColor || '#0A0A0A';
        const canvas = await window.html2canvas(target, {
            backgroundColor: bg,
            scale: 2,                 // 고해상도
            useCORS: true,
            logging: false,
        });
        const link = document.createElement('a');
        link.download = `dotwars-result-${Date.now()}.png`;
        link.href = canvas.toDataURL('image/png');
        link.click();
    } catch (e) {
        console.error('[download] 결과 이미지 생성 실패', e);
        // 폴백 — 캔버스만
        const link = document.createElement('a');
        link.download = `dotwars-final-${Date.now()}.png`;
        link.href = els.canvas.toDataURL('image/png');
        link.click();
    } finally {
        if (actions) actions.style.visibility = '';
        els.downloadBtn.disabled = false;
        els.downloadBtn.textContent = prevLabel;
    }
}
