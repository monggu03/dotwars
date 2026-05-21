/*
 * spike-1000.js — 한계 발견용 부하 테스트 (100 → 1500 VU 점진 증가).
 *
 * 목적: "어디서 처음 깨지는가" 를 관찰. 이전 100명 테스트(p95 16ms, 에러 0%)는 여유였으니
 *       이번엔 한계점을 찾는다.
 *
 * ── 왜 점진적 증가인가 ──────────────────────────────────────────────
 *  한 번에 1500 VU 를 줘도 되지만, 그러면 "깨졌다"만 알지 "어디서/왜" 를 모른다.
 *  100 → 500 → 1000 → 1500 으로 단계를 나누고 각 1분 유지하면,
 *  어느 구간에서 p95 가 치솟고 에러가 나기 시작하는지 = 한계점을 정확히 짚을 수 있다.
 *
 * ── 5초 sleep 의 의미 ───────────────────────────────────────────────
 *  실제 게임 클라이언트는 5초마다 폴링한다. 그래서 VU 1명 = 5초에 1회 요청.
 *  즉 1500 VU ≈ 초당 1500/5 × 3엔드포인트 = 약 900 req/s 부하.
 *  sleep 을 0 으로 하면 같은 VU 수로 훨씬 더 큰 부하가 되지만, 그건 비현실적.
 *  "실제 트래픽 패턴" 을 모방하려면 sleep 으로 폴링 주기를 맞춰야 한다.
 *
 * ── thresholds 를 느슨하게 잡은 이유 ────────────────────────────────
 *  이 테스트는 "통과/실패 판정" 이 목적이 아니라 "한계 관찰" 이 목적.
 *  너무 빡빡하면(p95<800) 500 VU 부터 빨갛게 떠서 그 이후 데이터를 못 본다.
 *  느슨하게(p95<2000, err<5%) 잡아 1500 까지 끝까지 돌려보고 곡선을 본다.
 *
 * ── 결과 JSON 저장 ──────────────────────────────────────────────────
 *  실행 시 플래그로 저장 (스크립트 수정 불필요):
 *    k6 run --summary-export=scripts/loadtest/summary.json scripts/loadtest/spike-1000.js
 *  시계열 전체가 필요하면:
 *    k6 run --out json=scripts/loadtest/raw.json scripts/loadtest/spike-1000.js
 *
 * ── 중요 caveat ─────────────────────────────────────────────────────
 *  1500 VU 를 노트북 1대 + 집 인터넷으로 쏘면, 서버보다 "클라이언트(내 PC) 한계" 가
 *  먼저 올 수 있다. 결과가 나쁘면 EC2 모니터(monitor.sh)의 CPU 가 한가한지 먼저 확인 —
 *  서버 CPU 가 한가한데 k6 가 느리면 그건 클라/네트워크 병목이지 서버 병목이 아니다.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = 'https://dotwars.kr';

// ── 커스텀 메트릭 ──────────────────────────────────────────────────
// 엔드포인트별 응답시간을 따로 봐야 "어느 API 가 먼저 느려지는가" 를 안다.
// (canvas 가 가장 무겁다 — 11×17 픽셀 배열 직렬화)
const canvasDur = new Trend('dur_canvas', true);
const statsDur  = new Trend('dur_stats', true);
const statusDur = new Trend('dur_status', true);
const errors    = new Rate('errors');

export const options = {
    // 각 단계 1분 유지 → 그 구간의 안정 상태(steady state) 관찰. 마지막 30초는 정상 종료.
    stages: [
        { duration: '1m',  target: 100 },
        { duration: '1m',  target: 500 },
        { duration: '1m',  target: 1000 },
        { duration: '1m',  target: 1500 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_failed:   ['rate<0.05'],    // 에러율 5% 미만 (느슨)
        http_req_duration: ['p(95)<2000'],   // p95 2초 미만 (느슨)
    },
};

export default function () {
    // batch — 한 VU 가 3개 엔드포인트를 동시에 (브라우저 병렬 요청 모방)
    const res = http.batch([
        ['GET', `${BASE}/api/game/canvas`],
        ['GET', `${BASE}/api/stats/factions`],
        ['GET', `${BASE}/api/game/status`],
    ]);

    canvasDur.add(res[0].timings.duration);
    statsDur.add(res[1].timings.duration);
    statusDur.add(res[2].timings.duration);

    const ok0 = check(res[0], { 'canvas 200': (r) => r.status === 200 });
    const ok1 = check(res[1], { 'stats 200':  (r) => r.status === 200 });
    const ok2 = check(res[2], { 'status 200': (r) => r.status === 200 });
    errors.add(!ok0 || !ok1 || !ok2);

    sleep(5);   // 실제 폴링 주기 모방
}
