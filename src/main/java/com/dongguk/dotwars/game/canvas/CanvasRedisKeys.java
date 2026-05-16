package com.dongguk.dotwars.game.canvas;

/**
 * Redis 키 네임스페이스 상수 모음.
 *
 * 한 곳에 모아 두는 이유:
 *  - 키 문자열을 코드 곳곳에 하드코딩하면 오타 1자로 다른 키를 가리키게 됨 → 미스 1번에 데이터 손실
 *  - 추후 prefix("dev:", "prod:") 도입 시 이 파일만 수정하면 됨
 *
 * Redis 네이밍 컨벤션: 도메인:서브도메인:식별자
 *  - canvas:current        → 캔버스 현재 상태 (Hash)
 *  - cooldown:user:{id}    → 사용자별 쿨다운 (String + TTL)
 *  - faction:count         → 진영별 픽셀 카운트 (Hash)
 *  - game:status           → 게임 라이프사이클 상태 (String)
 */
public final class CanvasRedisKeys {

    /** 캔버스 현재 상태. Hash 구조 — field "x,y" → value "factionId" */
    public static final String CANVAS_CURRENT = "canvas:current";

    /** 사용자별 쿨다운 키 prefix. 실제 키는 {@link #cooldownKey(Long)} 로 생성 */
    public static final String COOLDOWN_USER_PREFIX = "cooldown:user:";

    /** 진영별 픽셀 카운트. Hash 구조 — field "factionId" → value "count" (HINCRBY 로 증감) */
    public static final String FACTION_COUNT = "faction:count";

    /** 게임 라이프사이클 상태. String — "SCHEDULED" / "ACTIVE" / "FROZEN" / "ENDED" */
    public static final String GAME_STATUS = "game:status";

    private CanvasRedisKeys() {
        // 상수 클래스 — 인스턴스화 방지
    }

    /**
     * 캔버스 Hash 의 좌표 field 생성.
     * "x,y" 포맷 — 콤마 구분이 가장 흔한 관행. "(0,0)" 같은 괄호 포함은 redis-cli 가독성 ↓.
     */
    public static String pixelField(int x, int y) {
        return x + "," + y;
    }

    /**
     * 사용자별 쿨다운 키 생성. 예: cooldown:user:42
     */
    public static String cooldownKey(Long userId) {
        return COOLDOWN_USER_PREFIX + userId;
    }
}
