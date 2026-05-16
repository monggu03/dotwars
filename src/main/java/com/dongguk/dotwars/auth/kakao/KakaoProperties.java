package com.dongguk.dotwars.auth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 kakao.* 설정을 자바 객체로 바인딩.
 *
 * 왜 @ConfigurationProperties 인가:
 *  - @Value("${kakao.client-id}") 식의 산발적 주입은 키가 늘어나면 추적이 어렵고 오타도 잘 잡히지 않음.
 *  - record 로 묶어두면 어떤 설정이 필요한지 한 곳에서 한눈에 보이고, IDE 가 yml 키와 자동 매칭/경고 제공.
 *  - 테스트 시 record 를 그대로 new 해서 주입 가능 → 단위 테스트 단순화.
 *
 * record 컴포넌트 이름은 camelCase, yml 키는 kebab-case 라도 Spring 이 자동 매핑함
 * (client-id ↔ clientId).
 *
 * 활성화: DotwarsApplication 에 @ConfigurationPropertiesScan 을 붙여 자동 등록.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        // REST API 키 — 카카오 디벨로퍼스 "앱 키" 페이지에서 발급
        String clientId,
        // 보안 → Client Secret. 토큰 탈취 대비. application-secret.yml 에서 주입.
        String clientSecret,
        // 카카오에 등록한 콜백 URL 과 정확히 일치해야 함
        String redirectUri,
        // 인증 코드 발급 페이지 — /api/auth/kakao/login 이 여기로 리다이렉트
        String authorizationUri,
        // 액세스 토큰 발급 엔드포인트
        String tokenUri,
        // 사용자 정보 조회 엔드포인트
        String userInfoUri
) {}
