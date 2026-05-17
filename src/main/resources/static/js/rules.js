/*
 * rules.js — 진영 5개 + 각 진영의 단과대 목록을 GET /api/departments 로 로드 후 렌더.
 *
 * 데이터 흐름:
 *  - 서버가 진영 색/이름/단과대 목록 진실원천 → 클라이언트가 fetch 후 그대로 표시
 *  - 시드 데이터(진영/단과대) 가 바뀌어도 페이지 코드는 그대로 (변경 자동 반영)
 *
 * 인증 X — /api/departments 는 SecurityConfig 에서 permitAll. 로그인 안 한 방문자도 룰 페이지 가능.
 */

import { apiGet } from './api.js';

const $ = (id) => document.getElementById(id);
const els = {
    factionList: $('faction-list'),
};

init();

async function init() {
    try {
        const data = await apiGet('/api/departments');
        renderFactions(data.factions);
    } catch (e) {
        console.error('[rules] 진영 로드 실패', e);
        els.factionList.innerHTML = '<p class="loader">진영 정보를 불러올 수 없습니다.</p>';
    }
}

function renderFactions(factions) {
    els.factionList.innerHTML = factions
        .map((f) => {
            const depts = f.departments.map((d) => d.name).join(' · ');
            return `
                <article class="faction-card" style="--faction-color: ${f.colorHex}">
                    <span class="faction-card__dot"></span>
                    <div class="faction-card__body">
                        <span class="faction-card__name">${f.name}</span>
                        <span class="faction-card__departments">${depts}</span>
                    </div>
                </article>
            `;
        })
        .join('');
}
