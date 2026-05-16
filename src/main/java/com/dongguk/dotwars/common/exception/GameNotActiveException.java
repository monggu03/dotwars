package com.dongguk.dotwars.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 게임 상태가 ACTIVE 가 아닌데 픽셀 칠하기 시도한 경우.
 *
 * 403 Forbidden — 인증은 정상인데 현재 시점에 그 액션이 허용되지 않음.
 *
 * 가능한 비-ACTIVE 상태:
 *  - SCHEDULED: 게임 시작 전 (오늘 16:00 도래 전)
 *  - FROZEN:    매일 자정~다음날 16:00 사이
 *  - ENDED:     3일차 자정 통과 후
 */
public class GameNotActiveException extends BusinessException {
    public GameNotActiveException(String currentStatus) {
        super(HttpStatus.FORBIDDEN, "GAME_NOT_ACTIVE",
                "지금은 픽셀을 칠할 수 없습니다. (현재 상태: " + currentStatus + ")");
    }
}
