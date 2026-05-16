/*
 * websocket.js — STOMP over SockJS 클라이언트.
 *
 * 사용 라이브러리:
 *   - @stomp/stompjs v7 (UMD: window.StompJs.Client)
 *   - SockJS 1.x       (UMD: window.SockJS)
 *
 * 인증:
 *   HttpOnly 쿠키(accessToken) → SockJS HTTP 핸드셰이크 시 자동 동봉.
 *   서버의 JwtHandshakeInterceptor 가 쿠키에서 토큰 추출 → 검증.
 *   클라이언트는 토큰을 직접 다루지 않음 → XSS 토큰 탈취 방어 유지.
 *
 * 재연결:
 *   @stomp/stompjs 의 reconnectDelay 로 자동 재시도. 5초 간격.
 *   onConnect 가 매번 호출되므로 그 안에서 subscribe 재등록 + onReconnected 콜백.
 *
 * 단일 서버 한계:
 *   서버의 SimpleBroker 가 같은 JVM 클라이언트에게만 fan-out.
 *   멀티 인스턴스 시 다른 서버 사용자에겐 안 감 → 다음 단계에서 Redis Pub/Sub 도입 검토.
 */

let stompClient = null;
let connectCount = 0;
let callbacks = {};

/**
 * WebSocket 연결 시작.
 * @param {object} cb
 * @param {(msg) => void} cb.onPixelPainted    /topic/canvas 메시지 핸들러
 * @param {(msg) => void} cb.onGameStatusChanged /topic/game 메시지 핸들러
 * @param {() => void}    cb.onReconnected     재연결(2번째 이상 연결) 시 호출 — 캔버스 다시 받기용
 */
export function connectWebSocket(cb) {
    callbacks = cb;

    // CDN UMD 글로벌 객체
    const { Client } = window.StompJs;

    stompClient = new Client({
        // webSocketFactory: SockJS 인스턴스를 매번 새로 만들어 반환.
        //   재연결 시에도 새 SockJS 가 생성되어 핸드셰이크 → 쿠키 재검증.
        webSocketFactory: () => new SockJS('/ws'),

        // 재연결 정책 — 끊기면 5초 후 자동 재시도. 인증 실패(401)면 SockJS 가 즉시 close
        // → 재시도 무한 루프 가능성. 그러나 핸드셰이크에서 거절되면 토큰이 만료된 거라
        // 페이지 새로고침 또는 로그인 다시가 정답. 학습 단계에선 단순 재시도로 충분.
        reconnectDelay: 5000,

        // STOMP heart-beat — 연결이 살아있는지 4초마다 ping. 네트워크 끊김 빠른 감지.
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,

        // 디버그 로그 비활성 — 운영에선 콘솔 노이즈 줄임
        debug: () => {},

        onConnect: () => {
            connectCount++;
            const isReconnect = connectCount > 1;
            console.log(`[ws] connected (count=${connectCount}, reconnect=${isReconnect})`);

            // 매번 subscribe 재등록. 재연결 시 옛 구독은 서버에서 이미 사라졌으므로 새로.
            stompClient.subscribe('/topic/canvas', (frame) => {
                try {
                    const data = JSON.parse(frame.body);
                    callbacks.onPixelPainted?.(data);
                } catch (e) {
                    console.warn('[ws] canvas msg parse 실패', e);
                }
            });

            stompClient.subscribe('/topic/game', (frame) => {
                try {
                    const data = JSON.parse(frame.body);
                    callbacks.onGameStatusChanged?.(data);
                } catch (e) {
                    console.warn('[ws] game msg parse 실패', e);
                }
            });

            // 재연결인 경우 메시지 누락 보정 콜백 (캔버스 다시 받기 등)
            if (isReconnect) {
                callbacks.onReconnected?.();
            }
        },

        onStompError: (frame) => {
            // STOMP 프로토콜 레벨 에러. CONNECT 실패(인증 거절 등) 가 대표적.
            console.error('[ws] STOMP error:', frame.headers?.message, frame.body);
        },

        onWebSocketClose: (event) => {
            console.warn('[ws] socket closed code=', event.code);
        },

        onWebSocketError: (event) => {
            console.warn('[ws] socket error', event);
        },
    });

    stompClient.activate();
}

export function disconnectWebSocket() {
    if (stompClient) {
        stompClient.deactivate();
        stompClient = null;
        connectCount = 0;
    }
}
