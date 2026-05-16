/*
 * auth-guard.js — 인증 상태에 따른 페이지 분기 헬퍼.
 *
 * 각 페이지가 자기 요구사항에 맞게 호출:
 *
 *   - index.html      → ensureAnonymousOrRedirect(): 이미 로그인이면 다음 단계 페이지로
 *   - select-dept.html → ensureLoggedInWithoutDepartment(): 미로그인이면 로그인, 단과대 있으면 게임
 *   - game.html       → ensureLoggedInWithDepartment(): 미로그인이면 로그인, 미선택이면 단과대 선택
 *
 * 모두 GET /api/users/me 한 번 호출로 분기 결정.
 *  - 200 + department null   → 로그인은 됐고 단과대만 미선택
 *  - 200 + department 있음   → 로그인 + 단과대 완료 (게임 진행 가능)
 *  - 401                     → 미로그인
 */

import { apiGet, ApiError } from './api.js';

const ROUTE = {
    login: '/index.html',
    selectDepartment: '/select-department.html',
    game: '/game.html',
};

/**
 * 본인 정보 조회. 미인증이면 null 반환 (예외 throw 안 함 — 흔히 발생하는 케이스).
 * @returns {Promise<object|null>} UserMeResponse 또는 null
 */
export async function fetchMe() {
    try {
        return await apiGet('/api/users/me');
    } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
            return null;
        }
        // 그 외 에러(500 등) 는 호출자가 처리 — 일단 throw
        throw e;
    }
}

/**
 * index.html 용 — 이미 로그인 상태면 다음 단계로 보냄.
 * 그래야 로그인된 사용자가 메인 페이지에 다시 와도 자동 진행.
 */
export async function redirectIfLoggedIn() {
    const me = await fetchMe();
    if (!me) return;  // 미로그인 → 머무름
    location.replace(me.department ? ROUTE.game : ROUTE.selectDepartment);
}

/**
 * select-department.html 용 — 미로그인이면 index 로, 단과대 이미 있으면 game 으로.
 * 반환값: UserMeResponse (정상 진입한 경우)
 */
export async function requireLoginWithoutDepartment() {
    const me = await fetchMe();
    if (!me) {
        location.replace(ROUTE.login);
        return null;
    }
    if (me.department) {
        location.replace(ROUTE.game);
        return null;
    }
    return me;
}

/**
 * game.html 용 — 미로그인이면 index 로, 단과대 미선택이면 select 로.
 */
export async function requireLoginWithDepartment() {
    const me = await fetchMe();
    if (!me) {
        location.replace(ROUTE.login);
        return null;
    }
    if (!me.department) {
        location.replace(ROUTE.selectDepartment);
        return null;
    }
    return me;
}
