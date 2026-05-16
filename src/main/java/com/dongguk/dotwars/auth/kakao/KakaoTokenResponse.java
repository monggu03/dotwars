package com.dongguk.dotwars.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 /oauth/token 응답.
 *
 * Kakao 는 snake_case 로 응답하므로 @JsonProperty 로 자바 camelCase 와 매핑.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true):
 *   카카오가 미래에 새 필드를 추가해도 우리 record 에 없으면 무시. 외부 API 응답 DTO 의 정석.
 *   (Jackson 기본은 모르는 필드 발견 시 예외. 명시적으로 끄지 않으면 응답 스키마 변경 시 즉시 깨짐.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("refresh_token") String refreshToken,
        // 액세스 토큰 만료까지 남은 초. 우리는 JWT 자체 만료를 별도로 관리해서 직접 사용하진 않지만 디버깅에 유용.
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("refresh_token_expires_in") Integer refreshTokenExpiresIn,
        String scope
) {}
