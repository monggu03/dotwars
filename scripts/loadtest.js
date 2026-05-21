/*
 * loadtest.js — k6 부하 테스트 (읽기 경로 위주).
 *
 * 왜 읽기 경로인가:
 *  - 축제 트래픽의 대부분은 폴링(canvas/stats/status/online) — 100명이 5초마다 호출.
 *  - 페인트(쓰기)는 100명 × 1회/5초 = 초당 20건으로 가벼움 + JWT 인증 필요 → 별도 셋업.
 *  - 따라서 "서버가 버티나" 의 핵심 위험은 읽기 폴링 부하. 이것부터 검증.
 *
 * 실행:
 *   k6 run scripts/loadtest.js
 *
 * 결과 해석:
 *   - http_req_duration p(95) < 800ms  → 통과 (사용자 체감 쾌적)
 *   - http_req_failed rate < 1%        → 통과 (에러 거의 없음)
 *   - 둘 중 하나라도 빨간색이면 서버 증설/튜닝 검토.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = 'https://dotwars.kr';

export const options = {
    // 사용자 수를 점진적으로 늘렸다가 줄임 (실제 입장 패턴 모사)
    stages: [
        { duration: '30s', target: 50 },   // 0 → 50명 워밍업
        { duration: '1m',  target: 100 },  // 50 → 100명
        { duration: '2m',  target: 100 },  // 100명 유지 (피크)
        { duration: '30s', target: 0 },    // 정리
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'],  // 95% 요청이 800ms 이내
        http_req_failed:   ['rate<0.01'],  // 실패율 1% 미만
    },
};

export default function () {
    // 한 VU(가상 사용자)가 실제 게임 클라이언트처럼 4개 엔드포인트 폴링.
    // http.batch 로 4개를 동시에 쏨 (브라우저가 병렬로 요청하는 것과 유사).
    const responses = http.batch([
        ['GET', `${BASE}/api/game/canvas`],
        ['GET', `${BASE}/api/stats/factions`],
        ['GET', `${BASE}/api/stats/online`],
        ['GET', `${BASE}/api/game/status`],
    ]);

    check(responses[0], { 'canvas 200': (r) => r.status === 200 });
    check(responses[1], { 'stats 200':  (r) => r.status === 200 });
    check(responses[2], { 'online 200': (r) => r.status === 200 });
    check(responses[3], { 'status 200': (r) => r.status === 200 });

    sleep(5);   // 실제 클라이언트 폴링 주기(약 5초) 모사
}
