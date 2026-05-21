/*
 * admin.js — 관리자 사령부 대시보드.
 *
 * 흐름:
 *  1. /api/admin/check 로 관리자 여부 확인 → 아니면 접근 거부 화면
 *  2. 관리자면 3초마다 /api/admin/stats 폴링 → 각 위젯 갱신
 *
 * 비관리자/비로그인은 데이터 API(/api/admin/stats)가 어차피 403/401 → 셸만으론 노출 없음.
 */
import { apiGet } from './api.js';

const GRID_WIDTH = 11;
const GRID_HEIGHT = 17;
const POLL_MS = 3000;

const FACTION_COLORS = {
    0: '#FFFFFF', 1: '#FFA040', 2: '#F04545', 3: '#43D043', 4: '#3DA8DE', 5: '#B08AE0',
};

const $ = (id) => document.getElementById(id);

init();

async function init() {
    let admin = false;
    try {
        const res = await apiGet('/api/admin/check');
        admin = !!res.admin;
    } catch (_) { admin = false; }

    if (!admin) {
        $('admin').hidden = true;
        $('admin-denied').hidden = false;
        return;
    }

    $('admin').hidden = false;
    await refresh();
    setInterval(refresh, POLL_MS);
}

async function refresh() {
    let data;
    try {
        data = await apiGet('/api/admin/stats');
    } catch (e) {
        $('admin-updated').textContent = '갱신 실패 — 재시도 중';
        return;
    }
    renderKpis(data);
    renderFactions(data.factions);
    renderContested(data.cells);
    renderHeatmap(data.cells);
    renderHourly(data.hourly);
    renderRecent(data.recent);
    $('admin-updated').textContent = `갱신 ${new Date().toLocaleTimeString('ko-KR')}`;
}

function renderKpis(d) {
    $('kpi-online').textContent = d.online.toLocaleString();
    $('kpi-peak').textContent = d.peakOnline.toLocaleString();
    $('kpi-paints').textContent = d.totalPaints.toLocaleString();
    $('kpi-participants').textContent = d.participants.toLocaleString();
    $('kpi-ppm').textContent = `${d.paintsLastMinute.toLocaleString()}/분`;
}

function renderFactions(factions) {
    $('admin-factions').innerHTML = (factions || []).map((f) => `
        <li class="adm-faction" style="--c:${f.colorHex}; --pct:${f.percentage}%">
            <span class="adm-faction__dot" style="background:${f.colorHex}"></span>
            <div>
                <div class="adm-faction__name">${f.name}</div>
                <div class="adm-faction__bar"></div>
            </div>
            <span class="adm-faction__pct">${f.percentage.toFixed(1)}%</span>
        </li>
    `).join('');
}

function renderContested(cells) {
    const top = (cells || []).slice(0, 10);
    if (top.length === 0) {
        $('admin-contested').innerHTML = '<li style="color:var(--text-tertiary)">아직 데이터 없음</li>';
        return;
    }
    $('admin-contested').innerHTML = top.map((c) => `
        <li>
            <span class="adm-contested__coord">(${c.x},${c.y})</span>
            <span class="adm-contested__count">${c.count}회</span>
        </li>
    `).join('');
}

function renderHeatmap(cells) {
    const counts = new Map();
    let max = 0;
    for (const c of (cells || [])) {
        counts.set(`${c.x},${c.y}`, c.count);
        if (c.count > max) max = c.count;
    }
    let html = '';
    for (let y = 0; y < GRID_HEIGHT; y++) {
        for (let x = 0; x < GRID_WIDTH; x++) {
            const cnt = counts.get(`${x},${y}`) || 0;
            // 강도 0~1 → 빨강 밝기. 0이면 어두운 기본색.
            const intensity = max > 0 ? cnt / max : 0;
            const bg = cnt === 0
                ? 'var(--bg-base)'
                : `rgba(240, 69, 69, ${0.15 + intensity * 0.85})`;
            html += `<div class="adm-heatmap__cell" style="background:${bg}" title="(${x},${y}) ${cnt}회"></div>`;
        }
    }
    $('admin-heatmap').innerHTML = html;
}

function renderHourly(hourly) {
    const list = hourly || [];
    if (list.length === 0) {
        $('admin-hourly').innerHTML = '<div style="color:var(--text-tertiary);font-size:var(--text-xs)">아직 데이터 없음</div>';
        return;
    }
    const max = Math.max(...list.map((h) => h.count), 1);
    $('admin-hourly').innerHTML = list.map((h) => `
        <div class="adm-hour" style="--pct:${(h.count / max) * 100}%">
            <span class="adm-hour__label">${h.label}</span>
            <span class="adm-hour__bar"></span>
            <span class="adm-hour__count">${h.count}</span>
        </div>
    `).join('');
}

function renderRecent(recent) {
    const list = recent || [];
    if (list.length === 0) {
        $('admin-recent').innerHTML = '<li style="color:var(--text-tertiary)">아직 페인트 없음</li>';
        return;
    }
    $('admin-recent').innerHTML = list.map((p) => {
        const color = FACTION_COLORS[p.factionId] || '#888';
        const t = new Date(p.paintedAt).toLocaleTimeString('ko-KR', { hour12: false });
        return `
            <li>
                <span class="adm-log__dot" style="background:${color}"></span>
                <span class="adm-log__coord">(${p.x},${p.y})</span>
                <span class="adm-log__time">${t}</span>
            </li>
        `;
    }).join('');
}
