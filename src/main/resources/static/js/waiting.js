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
    // 새벽(00~08시) 야간 휴장이면 숙면 멘트, 그 외엔 자동 이동 안내.
    els.meta.textContent = isNightHours()
        ? '새벽에는 게임보다 숙면을 취하세요!'
        : '시작 시각이 되면 자동으로 게임 화면으로 이동합니다.';
    tick();
}

// 게임은 매일 08:00~24:00 운영 → 00:00~07:59 는 야간 휴장(새벽).
// 사용자 로컬 시각 기준 (한국 사용자는 KST = 운영 시각과 동일).
function isNightHours() {
    const h = new Date().getHours();
    return h >= 0 && h < 8;
}

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
