/*
 * game.js — placeholder.
 * 다음 step 에서 실제 캔버스 + WebSocket 로 교체.
 *
 * 현재 책임:
 *  - 인증 + 단과대 선택 완료 여부 확인 (아니면 적절한 페이지로 리다이렉트)
 *  - 본인 진영 색을 화면에 표시
 *  - 로그아웃 버튼
 */

import { apiPost } from './api.js';
import { requireLoginWithDepartment } from './auth-guard.js';

const els = {
    identity: document.getElementById('identity'),
    deptName: document.getElementById('dept-name'),
    factionName: document.getElementById('faction-name'),
    logout: document.getElementById('logout'),
};

init();

async function init() {
    const me = await requireLoginWithDepartment();
    if (!me) return;   // 리다이렉트 진행 중

    // 본인 진영색을 --faction-color 변수에 박음 → 카드 왼쪽 4px 라인 색 적용
    els.identity.style.setProperty('--faction-color', me.faction.colorHex);
    els.deptName.textContent = me.department.name;
    els.factionName.textContent = me.faction.name;

    els.logout.addEventListener('click', logout);
}

async function logout() {
    try {
        await apiPost('/api/auth/logout', {});
    } catch (e) {
        // 로그아웃은 멱등 — 실패해도 그냥 진행
        console.warn('[logout] 실패:', e);
    }
    location.replace('/index.html');
}
