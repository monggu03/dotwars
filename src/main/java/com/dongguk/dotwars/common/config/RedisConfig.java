package com.dongguk.dotwars.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 연동 설정.
 *
 * Spring Boot 가 application.yml 의 spring.data.redis 설정으로 RedisConnectionFactory(Lettuce) 를
 * 자동 구성. 우리는 그 위에 직렬화 정책만 명시한 RedisTemplate 빈을 올림.
 *
 * 왜 직접 만드나:
 *  - Spring Boot 의 기본 RedisTemplate<Object, Object> 는 JdkSerializationRedisSerializer 사용
 *    → redis-cli 로 보면 키/값이 바이너리로 보여 디버깅 불가능.
 *  - StringRedisTemplate(auto-configured) 도 옵션이지만, "왜 이렇게 만드는지" 명시적으로 보여주려고
 *    직접 정의. 학습 측면에서 직렬화 4종(key/value/hashKey/hashValue) 을 한눈에 인지하기 좋음.
 *
 * 우리 도메인의 모든 Redis 데이터:
 *   - 키:           "canvas:current", "cooldown:user:1", "faction:count", "game:status"
 *   - 값:           "ACTIVE" / "1" / "2" 같은 짧은 문자열 (수치는 String 으로 저장 후 파싱)
 *   - 해시 키/값:   "0,0" → "4" (x,y 좌표 → factionId)
 * 전부 문자열 → 직렬화 모두 StringRedisSerializer 통일.
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, String> — 4가지 serializer 모두 String 으로.
     *
     * 만약 하나라도 빠뜨리면 그 부분만 기본(JDK 직렬화)으로 떨어져
     * redis-cli 에서 "\xac\xed\x00\x05t\x00..." 같은 깨진 표시가 나옴.
     * 네 줄 다 명시하는 게 중요.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        // 초기화 — 트랜잭션 지원 활성/비활성 등 내부 상태 확정. 직접 new 했을 때 필수 호출.
        template.afterPropertiesSet();
        return template;
    }
}
