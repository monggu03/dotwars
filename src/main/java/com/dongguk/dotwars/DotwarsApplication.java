package com.dongguk.dotwars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

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
		// JVM 기본 타임존을 KST 로 고정 — 호스트(EC2/Docker) TZ 설정과 무관하게
		// LocalDateTime.now() / ZoneId.systemDefault() 가 항상 KST 가 되게 보장.
		// 게임 일정(08:00~24:00)·세션 전환·KST→UTC 변환이 이 전제에 의존하므로
		// SpringApplication.run 전(=어떤 빈도 시간 쓰기 전)에 명시적으로 박는다.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(DotwarsApplication.class, args);
	}

}
