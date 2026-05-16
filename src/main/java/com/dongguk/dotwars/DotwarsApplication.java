package com.dongguk.dotwars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// @ConfigurationProperties 가 붙은 모든 record/클래스를 빈으로 자동 등록.
// (KakaoProperties 처럼 application.yml 값을 타입 안전하게 받는 record 들)
@ConfigurationPropertiesScan
public class DotwarsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DotwarsApplication.class, args);
	}

}
