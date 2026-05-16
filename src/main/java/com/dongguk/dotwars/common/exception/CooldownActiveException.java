package com.dongguk.dotwars.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 쿨다운 중에 사용자가 다시 픽셀 칠하기 시도한 경우.
 *
 * 429 Too Many Requests — 사용자가 의도적으로 너무 빨리 요청을 보냄 (rate limit 의미).
 * remainingSec 을 들고 다녀서 응답 본문에 노출 → 클라이언트가 카운트다운 UI 표시 가능.
 */
@Getter
public class CooldownActiveException extends BusinessException {

    private final int remainingSec;

    public CooldownActiveException(int remainingSec) {
        super(HttpStatus.TOO_MANY_REQUESTS, "COOLDOWN_ACTIVE",
                "아직 쿨다운 중입니다. " + remainingSec + "초 후 다시 시도하세요.");
        this.remainingSec = remainingSec;
    }
}
