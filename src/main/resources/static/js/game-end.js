/*
 * game-end.js — placeholder.
 *
 * 게임 종료 후 진영별 최종 순위 표시.
 * 현재는 /api/stats/factions 응답을 그대로 사용 (게임 ENDED 직후에 Redis 카운트와 final_results 가 동일).
 * STEP 7 에서 /api/stats/final-result 별도 API 로 교체 예정.
 */

import { apiGet } from './api.js';

const $ = (id) => document.getElementById(id);

const els = {
    winnerCard: $('winner'),
    winnerName: $('winner-name'),
    winnerPct: $('winner-pct'),
    ranking: $('ranking'),
    backHome: $('back-home'),
};

init();

async function init() {
    try {
        const data = await apiGet('/api/stats/factions');
        render(data);
    } catch (e) {
        console.error('[game-end] 로드 실패', e);
    }

    els.backHome?.addEventListener('click', () => {
        location.replace('/');
    });
}

function render(data) {
    const ranked = data.factions;   // 이미 rank 순으로 정렬됨
    if (ranked.length === 0) return;

    const winner = ranked[0];
    els.winnerCard.style.setProperty('--faction-color', winner.colorHex);
    els.winnerName.textContent = winner.name;
    els.winnerPct.textContent = `${winner.percentage.toFixed(1)}%`;

    els.ranking.innerHTML = ranked
        .map((f) => `
            <li class="end-ranking__row" style="--row-color: ${f.colorHex}">
                <span class="end-ranking__rank">${f.rank}위</span>
                <span class="end-ranking__dot"></span>
                <span class="end-ranking__name">${f.name}</span>
                <span class="end-ranking__pct">${f.percentage.toFixed(1)}%</span>
            </li>
        `)
        .join('');
}
