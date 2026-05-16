package com.dongguk.dotwars.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러. 모든 컨트롤러에서 발생한 예외를 한 곳에서 일관 응답으로 변환.
 *
 * 핸들러 우선순위 (구체 → 일반):
 *  1) BusinessException                    — 도메인 정책 위반. 자기 자신의 HttpStatus 사용
 *  2) MethodArgumentNotValidException      — @Valid 검증 실패. 400 + fieldErrors 본문
 *  3) IllegalArgumentException             — 잘못된 입력 (서비스가 직접 던지는 경우). 400
 *  4) Exception                            — 그 외 알 수 없는 장애. 500 (스택 노출 X)
 *
 * 모든 응답은 ErrorResponse 형식으로 통일 → 클라이언트가 한 형식만 다루면 됨.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 쿨다운 예외 전용 핸들러 — 일반 BusinessException 보다 먼저 매칭됨 (구체 → 일반).
     * cooldownRemainingSec 필드를 응답에 포함시키기 위해 본문 형태가 다름.
     */
    @ExceptionHandler(CooldownActiveException.class)
    public ResponseEntity<CooldownErrorResponse> handleCooldown(CooldownActiveException e) {
        log.info("[business] COOLDOWN_ACTIVE remaining={}s", e.getRemainingSec());
        return ResponseEntity
                .status(e.getStatus())
                .body(new CooldownErrorResponse(
                        e.getErrorCode(),
                        e.getMessage(),
                        e.getRemainingSec()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        // INFO 레벨 — 정상 흐름에서도 발생할 수 있는 "예측 가능한 실패" (정책 위반).
        // 굳이 ERROR 로 두면 운영 모니터링에서 노이즈가 됨.
        log.info("[business] {} - {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // 어떤 필드들이 어떤 메시지로 실패했는지 응답에 담아줌 → 클라이언트가 폼 필드별로 에러 표시 가능.
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.info("[validation] fields={}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation("입력값 검증에 실패했습니다", fieldErrors));
    }

    /**
     * 서비스가 직접 던진 IllegalArgumentException. (예: agreed=false)
     * 비즈니스 예외로 정의할 만큼 자주 발생하지 않거나 한 곳에서만 쓰는 경우 활용.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.info("[illegal-arg] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    /**
     * 존재하지 않는 정적 리소스 요청 — 404 가 적절.
     *
     * Spring 6 부터 정적 리소스 미발견 시 NoResourceFoundException 을 던지는데,
     * 우리 catch-all 이 이걸 500 으로 떨어트리면 브라우저의 자동 favicon.ico /
     * .well-known/* 요청이 매번 ERROR 로그를 남기고 응답도 500. → 404 가 정상.
     *
     * 응답 본문은 만들지 않음 (브라우저가 어차피 무시하므로 페이로드 절약).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
        // 로그도 안 남김 — 단순 404 는 노이즈. 디버깅 시 access log 로 충분.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Content-Type 미스매치 — 415 Unsupported Media Type.
     *
     * 흔한 원인: 클라이언트가 JSON body 보내면서 Content-Type 헤더를 빠뜨림 → 기본 text/plain.
     * Spring 의 @RequestBody 가 JSON 만 받게 등록돼있어 거절. 클라이언트 버그 신호라 400대 응답.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.info("[media-type] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type 이 지원되지 않습니다. application/json 으로 보내주세요."));
    }

    /**
     * 잘못된 HTTP 메서드 (예: POST 만 받는 라우트에 GET) — 405 Method Not Allowed.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.info("[method-not-allowed] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of("METHOD_NOT_ALLOWED", e.getMessage()));
    }

    /**
     * 그 외 모든 예외. 시스템 장애 신호 → ERROR 레벨로 스택 로그 + 일반 메시지 응답.
     *
     * 응답에 스택 트레이스 노출하지 않음 — 보안상 위험 (서버 구조 추론 가능).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("[unknown] 처리되지 않은 예외", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "서버에 문제가 발생했습니다"));
    }
}
