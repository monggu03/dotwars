package com.dongguk.dotwars.common.config;

import com.dongguk.dotwars.auth.jwt.JwtAuthenticationFilter;
import com.dongguk.dotwars.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정 — JWT 기반 무상태 인증.
 *
 * 인증/인가 정책:
 *  - 공개(permitAll): 헬스체크, 카카오 OAuth 흐름, 단과대 목록, 캔버스 조회/상태, 통계, WebSocket, 정적 리소스
 *  - 그 외 모든 요청: JWT 검증 필요 (JwtAuthenticationFilter 가 검증 후 SecurityContext 채움)
 *
 * 끄는 것:
 *  - csrf: 토큰 헤더/쿠키로 인증 → 폼 기반 CSRF 보호 불필요
 *  - formLogin/httpBasic: 카카오 OAuth 만 사용
 *  - session: STATELESS — 매 요청 토큰 재검증, 서버 메모리 상태 없음 → 수평 확장 용이
 *
 * 켜는 것:
 *  - cors: 개발 중엔 모든 origin 허용 + credentials 통과. 운영 단계에서 화이트리스트로 좁힐 것.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // STATELESS → JSESSIONID 발급/추적 안 함. 매 요청이 독립.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 아래 corsConfigurationSource 빈을 자동으로 선택해 CORS 활성
                .cors(Customizer.withDefaults())

                // 미인증 사용자가 보호된 자원 요청 시: 401 Unauthorized (Spring Security 기본은 403).
                //  - 401 = "토큰 필요" (REST API 표준)
                //  - 403 = "권한 부족" (인증은 됐는데 못함)
                // JWT stateless 환경에선 미인증 = 401 이 의도. AuthenticationEntryPoint 로 명시.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                ))

                .authorizeHttpRequests(auth -> auth
                        // ── 헬스체크 / 인증 흐름 ─────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // /api/auth/kakao/login, /api/auth/kakao/callback, /api/auth/logout 모두 포함
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── 공개 조회 API ──────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/departments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/game/canvas", "/api/game/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stats/**").permitAll()

                        // ── WebSocket ─────────────────────────────────────
                        // /ws 핸드셰이크/메시지 라우팅. 메시지 단위 인증은 WebSocketConfig 에서 별도 처리.
                        .requestMatchers("/ws/**").permitAll()

                        // ── 정적 리소스 ────────────────────────────────────
                        // PathRequest.toStaticResources().atCommonLocations():
                        //   /css/**, /js/**, /images/**, /webjars/**, /favicon.ico 등 표준 위치 일괄 허용.
                        //   HTML 파일은 위치가 자유로워 별도 매처가 필요.
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/game.html",
                                "/game-end.html",
                                "/rules.html",
                                "/waiting.html",
                                "/select-department.html"
                        ).permitAll()

                        // ── 그 외 ───────────────────────────────────────────
                        .anyRequest().authenticated()
                )

                // JWT 검증 필터를 Spring Security 기본 인증 필터 앞에 끼움.
                // UsernamePasswordAuthenticationFilter 는 폼 로그인 처리용 — 우리는 안 쓰지만
                // 그 위치가 "인증 필터 자리" 의 표준 마커로 쓰임. JWT 필터를 그 앞에 두면
                // 도착한 요청이 가장 먼저 토큰 검증을 거침.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * CORS — 개발 단계에서는 모든 origin 허용. 운영에서는 dotwars.kr 만 허용으로 좁힐 것.
     *
     * 주의: setAllowCredentials(true) 와 setAllowedOrigins("*") 는 동시 불가.
     *   → setAllowedOriginPatterns("*") 로 우회. Spring Security 6 부터 권장되는 패턴.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 쿠키 기반 인증(accessToken HttpOnly 쿠키) 을 허용하려면 credentials 통과 필수
        config.setAllowCredentials(true);
        // 브라우저가 preflight 응답을 캐시할 수 있는 시간(초). 너무 짧으면 OPTIONS 요청 폭주.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
