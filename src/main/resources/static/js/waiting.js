/*
 * waiting.js — 세션 시작까지 카운트다운 + 자동 이동.
 *
 * 흐름:
 *  1. /api/game/status 폴링 (10초 간격)
 *  2. status === 'ACTIVE'  → /game.html 로 리다이렉트
 *  3. status === 'ENDED'   → /game-end.html 로 리다이렉트
 *  4. status === 'FROZEN' or 'SCHEDULED' → nextSession.startsAt 으로 카운트다운
 *
 * 1초마다 카운트다운 텍스트 갱신, 10초마다 status 재조회로 ACTIVE 전환 감지.
 *
 * 왜 WebSocket 안 씀:
 *  - 이 페이지에 있는 사용자는 게임 시작 전이라 WebSocket 핸드셰이크 비용이 아까움.
 *  - 10초 폴링이면 ACTIVE 전환 후 최대 10초 지연 — 휴식 화면에서 충분히 허용 가능.
 */

import { apiGet, ApiError } from './api.js';
import { inviteFriends } from './share.js';

const STATUS_POLL_MS = 10_000;
const TICK_MS = 1_000;

const $ = (id) => document.getElementById(id);
const els = {
    header: $('waiting-header'),
    factionDot: $('my-faction-dot'),
    factionName: $('my-faction-name'),
    label: $('waiting-label'),
    countdown: $('waiting-countdown'),
    hint: $('waiting-hint'),
    meta: $('waiting-meta'),
    inviteFriends: $('invite-friends'),
};

els.inviteFriends?.addEventListener('click', () => inviteFriends());

let nextStartsAt = null;

init().catch((e) => console.error('[waiting] init 실패', e));

async function init() {
    // 본인 진영 표시는 로그인+단과대 선택자만. 실패 OK.
    apiGet('/api/users/me').then((me) => {
        if (me?.faction) {
            els.factionDot.style.background = me.faction.colorHex;
            els.factionName.textContent = me.faction.name;
            els.header.hidden = false;
        }
    }).catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) console.warn('[waiting] /me 실패', e);
    });

    await refreshStatus();
    setInterval(refreshStatus, STATUS_POLL_MS);
    setInterval(tick, TICK_MS);
}

async function refreshStatus() {
    const status = await apiGet('/api/game/status').catch(() => null);
    if (!status) return;

    if (status.status === 'ACTIVE') {
        location.replace('/game.html');
        return;
    }
    if (status.status === 'ENDED') {
        location.replace('/game-end.html');
        return;
    }

    // FROZEN 또는 SCHEDULED
    const next = status.nextSession;
    if (next?.startsAt) {
        nextStartsAt = new Date(next.startsAt);
        const hm = nextStartsAt.toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
        });
        els.label.textContent = `Day ${next.dayNumber} · ${hm} 시작`;
    } else {
        // 다음 세션 정보 없음 — 모든 세션 종료 직전 같은 드문 케이스
        nextStartsAt = null;
        els.label.textContent = '휴식 시간';
        els.countdown.textContent = '';
    }
    // 새벽(00~08시) 야간 휴장이면 픽셀 달 + 숙면 멘트, 그 외엔 자동 이동 안내.
    // innerHTML 이지만 전부 정적 문자열이라 XSS 위험 없음.
    els.meta.innerHTML = isNightHours()
        ? `새벽에는 숙면을 취하세요 ${MOON_SVG}`
        : '시작 시각이 되면 자동으로 게임 화면으로 이동합니다.';
    pollWaiting();   // 카운트다운 밑 "현재 N명 대기 중!" 갱신 (핑 겸 조회)
    tick();
}

// ── 대기 인원 presence ────────────────────────────────────────────────
// 대기화면은 WebSocket 을 안 쓰므로(시작 전 핸드셰이크 비용 회피) 별도 핑으로 집계.
// 방문자 고유 id 를 localStorage 에 보관 → 같은 사람 여러 탭/새로고침은 1명으로.
let visitorId;
function getVisitorId() {
    if (visitorId) return visitorId;
    try {
        let id = localStorage.getItem('dotwars_visitor_id');
        if (!id) {
            id = crypto.randomUUID();
            localStorage.setItem('dotwars_visitor_id', id);
        }
        visitorId = id;
    } catch {
        // private 모드 등 localStorage 차단 시 — 휘발성 id (이번 로드 한정)
        visitorId = crypto.randomUUID();
    }
    return visitorId;
}

async function pollWaiting() {
    try {
        const res = await apiGet(`/api/stats/waiting?v=${encodeURIComponent(getVisitorId())}`);
        if (res && typeof res.waiting === 'number') {
            els.hint.textContent = `현재 ${res.waiting}명 대기 중!`;
        }
    } catch {
        // 실패해도 기존 텍스트("남음") 유지 — 조용히 무시.
    }
}

// 게임은 매일 08:00~24:00 운영 → 00:00~07:59 는 야간 휴장(새벽).
// 사용자 로컬 시각 기준 (한국 사용자는 KST = 운영 시각과 동일).
function isNightHours() {
    const h = new Date().getHours();
    return h >= 0 && h < 8;
}

// 픽셀 초승달 (8×8, 폭 3의 호). game-end 의 픽셀 하트와 같은 crispEdges 방식.
const MOON_SVG =
    '<svg class="waiting-moon" viewBox="0 0 8 8" shape-rendering="crispEdges" aria-hidden="true">'
    + '<g fill="currentColor">'
    + '<rect x="2" y="0" width="3" height="1"/>'
    + '<rect x="1" y="1" width="3" height="1"/>'
    + '<rect x="0" y="2" width="3" height="1"/>'
    + '<rect x="0" y="3" width="3" height="1"/>'
    + '<rect x="0" y="4" width="3" height="1"/>'
    + '<rect x="0" y="5" width="3" height="1"/>'
    + '<rect x="1" y="6" width="3" height="1"/>'
    + '<rect x="2" y="7" width="3" height="1"/>'
    + '</g></svg>';

function tick() {
    if (!nextStartsAt) return;
    const ms = nextStartsAt.getTime() - Date.now();
    if (ms <= 0) {
        // 시작 시각 도달 → status 재조회 트리거 (서버가 ACTIVE 로 전환됐을 것)
        els.countdown.textContent = '00:00:00';
        nextStartsAt = null;
        refreshStatus();
        return;
    }
    const total = Math.floor(ms / 1000);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    els.countdown.textContent =
        `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
