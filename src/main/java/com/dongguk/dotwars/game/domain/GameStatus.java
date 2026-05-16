package com.dongguk.dotwars.game.domain;

/**
 * 게임의 전체 라이프사이클 상태.
 *
 * - SCHEDULED: 시드 직후, 시작 시각 도래 전. 픽셀 칠하기 불가.
 * - ACTIVE   : 진행 중 (매일 16:00~24:00 의 active 시간대).
 * - FROZEN   : 자정~다음날 16:00 사이. 캔버스 상태는 보존되지만 칠하기 불가.
 * - ENDED    : 3일차 24:00 통과. 최종 결과 집계 후 영구 freeze.
 *
 * @Enumerated(EnumType.STRING) 으로 저장 → DB 에 "ACTIVE" 처럼 사람이 읽을 수 있는 문자열로.
 * ORDINAL(숫자) 저장은 enum 순서가 바뀌면 의미가 깨져서 매우 위험.
 */
public enum GameStatus {
    SCHEDULED,
    ACTIVE,
    FROZEN,
    ENDED
}
