/*
 * index.js — 메인 페이지 진입 로직.
 * 이미 로그인 상태면 다음 단계 페이지로 자동 이동.
 */

import { redirectIfLoggedIn } from './auth-guard.js';

// 페이지 로드 직후 본인 정보 확인 — 이미 로그인이면 다음 페이지로
redirectIfLoggedIn();

// 카카오 로그인 버튼은 단순 링크 동작이라 JS 처리 불필요 (<a href> 로 처리)
