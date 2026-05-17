package com.dongguk.dotwars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration 제외 — 우리는 JWT 만 사용.
// 기본 활성화 시 inMemoryUserDetailsManager + 매 부팅 시 랜덤 패스워드 로그가 찍힘
//   ("Using generated security password: ...")
// 우리는 SecurityFilterChain 에서 formLogin/httpBasic 둘 다 disable + JwtAuthenticationFilter 사용
// → inMemory user 가 아무 역할도 안 함. 자동 설정만 끄면 로그 깨끗 + 의도 명확.
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
// @ConfigurationProperties 가 붙은 모든 record/클래스를 빈으로 자동 등록.
// (KakaoProperties 처럼 application.yml 값을 타입 안전하게 받는 record 들)
@ConfigurationPropertiesScan
// @Scheduled 메서드를 활성화. GameScheduler 의 매초 tick 동작 enable.
@EnableScheduling
public class DotwarsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DotwarsApplication.class, args);
	}

}
