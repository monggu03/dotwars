/*
 * select-department.js — 단과대 선택 페이지 로직.
 *
 * 흐름:
 *  1. 인증 분기 (auth-guard 가 처리)
 *  2. GET /api/departments 로 진영/단과대 목록 불러와 렌더
 *  3. 단과대 라디오 + 약속 체크박스 → 둘 다 true 면 게임 시작 버튼 활성
 *  4. POST /api/users/me/department 호출 → 성공 시 /game.html 이동
 */

import { apiGet, apiPost, ApiError } from './api.js';
import { requireLoginWithoutDepartment } from './auth-guard.js';

const els = {
    body: document.getElementById('department-body'),
    agreement: document.getElementById('agreement'),
    submit: document.getElementById('submit'),
    error: document.getElementById('error'),
};

let selectedDepartmentId = null;

init();

async function init() {
    // 인증 + 단과대 미선택 상태 확인. 잘못된 페이지면 자동 리다이렉트.
    const me = await requireLoginWithoutDepartment();
    if (!me) return;   // 리다이렉트 진행 중

    try {
        const data = await apiGet('/api/departments');
        renderFactions(data.factions);
        bindEvents();
    } catch (e) {
        showError('단과대 목록을 불러오지 못했습니다. 새로고침 해주세요.');
        console.error(e);
    }
}

/**
 * 진영별 카드 + 단과대 라디오 렌더.
 * 진영 색은 --faction-color CSS 변수에 인라인 주입 (카드 배경은 칠 X, 왼쪽 4px 라인만).
 */
function renderFactions(factions) {
    els.body.innerHTML = '';   // 로딩 텍스트 제거
    for (const faction of factions) {
        const group = document.createElement('section');
        group.className = 'faction-group';
        group.style.setProperty('--faction-color', faction.colorHex);

        // 진영 헤더 (왼쪽 4px 라인 + 진영명)
        const header = document.createElement('div');
        header.className = 'faction-group__header';
        header.textContent = faction.name;
        group.appendChild(header);

        // 단과대 리스트
        const list = document.createElement('div');
        list.className = 'faction-group__list';
        for (const dept of faction.departments) {
            list.appendChild(renderDepartmentOption(dept, faction.colorHex));
        }
        group.appendChild(list);

        els.body.appendChild(group);
    }
}

/**
 * 단과대 1개 = <label class="radio-option"> 으로 통째로 큰 클릭 영역.
 * 선택 시 진영색 보더 (CSS 의 :has(input:checked) 로 처리).
 */
function renderDepartmentOption(dept, factionColor) {
    const label = document.createElement('label');
    label.className = 'radio-option';
    label.style.setProperty('--faction-color', factionColor);

    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'department';
    input.value = String(dept.id);
    input.addEventListener('change', () => {
        selectedDepartmentId = dept.id;
        updateSubmitState();
    });

    const span = document.createElement('span');
    span.textContent = dept.name;

    label.appendChild(input);
    label.appendChild(span);
    return label;
}

function bindEvents() {
    els.agreement.addEventListener('change', updateSubmitState);
    els.submit.addEventListener('click', onSubmit);
}

/** 단과대 선택 + 약속 체크 둘 다 true 일 때만 버튼 활성. */
function updateSubmitState() {
    const ok = selectedDepartmentId !== null && els.agreement.checked;
    els.submit.disabled = !ok;
}

async function onSubmit() {
    // 더블 클릭 방지
    els.submit.disabled = true;
    hideError();

    try {
        await apiPost('/api/users/me/department', {
            departmentId: selectedDepartmentId,
            agreed: els.agreement.checked,
        });
        // 성공 → 게임 화면으로
        location.replace('/game.html');
    } catch (e) {
        if (e instanceof ApiError) {
            // 이미 선택된 경우(409) — 도메인 정책 위반
            if (e.errorCode === 'DEPARTMENT_ALREADY_SET') {
                showError('이미 단과대를 선택하셨습니다. 게임 화면으로 이동합니다.');
                setTimeout(() => location.replace('/game.html'), 1500);
                return;
            }
            showError(e.message);
        } else {
            showError('알 수 없는 오류가 발생했습니다.');
        }
        // 에러 시 버튼은 다시 활성 (사용자가 재시도 가능하게)
        updateSubmitState();
    }
}

function showError(message) {
    els.error.textContent = message;
    els.error.classList.add('visible');
}
function hideError() {
    els.error.classList.remove('visible');
}
