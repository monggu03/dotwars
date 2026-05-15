package com.dongguk.dotwars.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 최소 설정 (D-14 단계).
 *
 * 현재 시점에서는 다음만 처리:
 *  - /api/health 와 정적 리소스는 인증 없이 통과
 *  - 그 외 모든 요청은 일단 인증 필요 (실제 인증 로직은 다음 단계의 JWT 필터에서)
 *  - REST API + JWT 기반이므로 세션/CSRF/폼로그인/Basic 모두 끔
 *
 * 카카오 OAuth 콜백 처리, JWT 필터, WebSocket Origin 검증 등은
 * 인증 단계(다음 Step)에서 이 클래스에 점진적으로 덧붙일 예정.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 토큰은 브라우저 세션을 쓰는 서버 렌더링 앱에 필요.
                // 우리는 JWT(토큰을 헤더로 들고 다님) 기반이므로 비활성화.
                .csrf(csrf -> csrf.disable())

                // 폼 로그인 페이지 자동 생성 끔 — 카카오 OAuth만 쓸 예정
                .formLogin(form -> form.disable())

                // HTTP Basic 인증 끔 — 기본 활성화 시 응답 헤더로 WWW-Authenticate 가 추가되어 혼란 야기
                .httpBasic(basic -> basic.disable())

                // 세션 비활성화 (JWT 무상태 인증).
                // 매 요청마다 토큰 검증 → 서버는 사용자 상태를 메모리에 들고있지 않음 → 수평 확장 용이
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 라우트별 인가 규칙
                .authorizeHttpRequests(auth -> auth
                        // 헬스체크는 누구나 호출 가능해야 함 (로드밸런서/모니터링)
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // 카카오 로그인 시작/콜백은 인증 전에 호출되므로 미리 열어둠
                        .requestMatchers("/api/auth/**").permitAll()
                        // 단과대 목록 같이 공개돼도 무방한 정보는 추후 명시적으로 열기
                        // (지금은 명시 안 함 — 의도치 않은 노출 방지)
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
