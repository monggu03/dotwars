package com.dongguk.dotwars.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 서비스 살아있음 여부를 외부에서 확인하는 가장 가벼운 엔드포인트.
 *
 * 용도:
 *  - 로컬 개발 중 "스프링 부트가 부팅됐나" 빠르게 확인
 *  - AWS ALB/타겟그룹 헬스체크 경로로 사용 (의존성이 죽어도 일단 200을 돌려줄 가벼운 응답이 필요)
 *  - 운영 모니터링(예: UptimeRobot)이 주기적으로 호출
 *
 * DB/Redis 까지 같이 검사하는 "심층 헬스체크"는 추후 spring-boot-actuator 의 /actuator/health 로 분리 예정.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 헬스 응답 DTO — record로 작성.
     *
     * record 선택 이유:
     *  - 불변(immutable) 응답 객체에 가장 적합 (DTO 의도와 정확히 맞음)
     *  - equals/hashCode/toString 자동 생성 → 보일러플레이트 0
     *  - Jackson 이 record 의 컴포넌트 이름을 그대로 JSON 키로 사용 (Java 16+)
     */
    public record HealthResponse(
            String status,        // "ok" | (이후 디그레이드 시 "degraded")
            String service,       // 서비스 식별자 — 여러 마이크로서비스가 있을 때 어디로부터 응답인지 구분
            String timestamp      // ISO 8601 UTC. 클라이언트가 응답 시각을 신뢰 가능하게 받는 용도
    ) {}

    @GetMapping
    public HealthResponse health() {
        // Instant.now().toString() → 항상 UTC + ISO 8601 (예: "2026-05-15T07:30:00.123Z")
        // KST 로 변환하지 않는 이유: 서버 간 시간 비교/로그 일관성 확보를 위해 모든 시간은 UTC 로 노출
        return new HealthResponse("ok", "dotwars", Instant.now().toString());
    }
}
