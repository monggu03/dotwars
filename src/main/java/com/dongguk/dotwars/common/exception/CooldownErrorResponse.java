package com.dongguk.dotwars.common.exception;

/**
 * 쿨다운 전용 에러 응답 — 표준 ErrorResponse 에 cooldownRemainingSec 추가.
 *
 * 일반 ErrorResponse 에 풀어 넣을 수도 있지만, 한 가지 케이스만 필드 추가하면 응답 스키마가
 * 케이스별로 달라져 클라이언트 코드가 복잡해짐. 차라리 별도 record 로 분리하고 컨트롤러 단에서
 * 명시적으로 다른 본문을 내려보내는 게 깔끔.
 */
public record CooldownErrorResponse(
        String error,
        String message,
        int cooldownRemainingSec
) {}
