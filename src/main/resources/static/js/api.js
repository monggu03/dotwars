/*
 * api.js — fetch 헬퍼.
 *
 * 모든 페이지가 import 해서 쓰는 공통 API 호출 모듈.
 * 쿠키 자동 동봉(credentials: 'include'), JSON 파싱, 에러 응답 정규화를 한 곳에 모음.
 *
 * 핵심:
 *  - 401 → 로그인 페이지로 자동 리다이렉트 (선택 가능)
 *  - 4xx/5xx → ApiError 로 throw, 호출자는 try/catch 로 잡음
 *  - 응답이 JSON 이 아닌 경우(204 No Content 등) 도 정상 처리
 */

/**
 * 모든 fetch 의 공통 옵션.
 * credentials: 'include' 가 핵심 — accessToken 쿠키를 함께 보냄.
 * SecurityConfig 의 CORS 가 allowCredentials=true 라 동작.
 */
const DEFAULT_INIT = {
    credentials: 'include',
    headers: {
        'Accept': 'application/json',
    },
};

/**
 * API 에러를 표현하는 커스텀 Error.
 * status, errorCode(서버의 errorCode 또는 HTTP status string), message, fieldErrors 를 들고 다님.
 */
export class ApiError extends Error {
    constructor(status, errorCode, message, fieldErrors) {
        super(message || `HTTP ${status}`);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors || null;
    }
}

/**
 * 내부 — fetch 결과를 정규화.
 * 성공: 응답 본문(JSON)을 반환. 본문이 없으면 null.
 * 실패: ApiError throw.
 */
async function handle(response) {
    // 204 No Content 처럼 본문이 없는 응답
    if (response.status === 204) {
        return null;
    }

    const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
    const body = isJson ? await response.json().catch(() => null) : null;

    if (response.ok) {
        return body;
    }

    // 에러 응답 — ErrorResponse record 형식이면 그대로 가져옴
    const errorCode = body?.error || `HTTP_${response.status}`;
    const message = body?.message || response.statusText || '알 수 없는 오류';
    throw new ApiError(response.status, errorCode, message, body?.fieldErrors);
}

/**
 * GET 요청.
 * @param {string} url
 * @param {object} [options] - 추가 fetch 옵션 (예: { signal } )
 */
export async function apiGet(url, options = {}) {
    const response = await fetch(url, {
        ...DEFAULT_INIT,
        ...options,
        method: 'GET',
        headers: { ...DEFAULT_INIT.headers, ...(options.headers || {}) },
    });
    return handle(response);
}

/**
 * POST 요청. body 는 JSON 직렬화됨.
 */
export async function apiPost(url, body, options = {}) {
    const response = await fetch(url, {
        ...DEFAULT_INIT,
        ...options,
        method: 'POST',
        headers: {
            ...DEFAULT_INIT.headers,
            'Content-Type': 'application/json',
            ...(options.headers || {}),
        },
        body: JSON.stringify(body),
    });
    return handle(response);
}
