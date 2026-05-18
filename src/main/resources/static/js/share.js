/*
 * share.js — 친구 초대 공유 모듈 (카톡/SNS/시스템).
 *
 * 전략:
 *  1) Web Share API (navigator.share) — iOS/Android 시스템 공유 다이얼로그.
 *     사용자가 카톡/메시지/디스코드/메일 등 자유롭게 선택. 카톡 미리보기는
 *     서버 응답의 OG 태그가 자동으로 잡아줌 (별도 Kakao SDK 불필요).
 *  2) 폴백: clipboard 복사 — Web Share API 미지원 (대부분 데스크탑 브라우저).
 *     사용자가 직접 카톡에 붙여넣음.
 *
 * 왜 Kakao SDK 안 씀:
 *  - SDK 등록/init/key 노출 등 추가 작업 필요
 *  - Web Share API + OG 태그 조합으로 동일 효과
 *  - 카톡 외 채널(디스코드, 메시지)도 같은 코드로 커버
 */

const SHARE_DATA = {
    title: 'dotwars — 동국대학교 픽셀 점령전',
    text: '동국대 축제 픽셀 점령전 같이 하자! 단과대 진영색으로 캔버스 점령하기.',
    url: 'https://dotwars.kr/',
};

/**
 * 친구 초대 공유 시도.
 *  - Web Share API 가능: 시스템 공유 다이얼로그 띄움
 *  - 불가능 또는 사용자 취소(폴백 X) 외 실패: 클립보드 복사 + 토스트
 *
 * @returns Promise<void>
 */
export async function inviteFriends() {
    // canShare 가 없는 환경(구식)도 있으니 share 자체로 분기
    if (navigator.share) {
        try {
            await navigator.share(SHARE_DATA);
            return;   // 사용자가 어디로 공유했든 성공
        } catch (e) {
            // 사용자가 다이얼로그 취소한 경우는 AbortError — 폴백 X (성공/취소 동등)
            if (e?.name === 'AbortError') return;
            // 그 외 실패(예: SecurityError) → 폴백으로
        }
    }
    await copyToClipboard();
}

async function copyToClipboard() {
    try {
        await navigator.clipboard.writeText(SHARE_DATA.url);
        showToast('링크 복사됨 — 카톡에 붙여넣으세요');
    } catch (e) {
        // clipboard 도 실패 — 매우 드물지만 (HTTPS 아닌 환경 등)
        // prompt 로 사용자가 수동 복사하도록
        prompt('아래 링크를 복사해서 공유하세요:', SHARE_DATA.url);
    }
}

// ── 토스트 (game.js 와 동일 패턴, 모듈별 독립 사용 가능) ─────────────

function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
}
