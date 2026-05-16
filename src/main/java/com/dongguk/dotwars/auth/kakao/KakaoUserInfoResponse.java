package com.dongguk.dotwars.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 카카오 /v2/user/me 응답에서 우리가 쓰는 부분만.
 *
 * 실제 응답에는 connected_at, properties, kakao_account 등 많은 필드가 더 있지만
 * "닉네임 동의 안 받기" 결정에 따라 id 만 사용.
 *
 * Long id 인 이유: 카카오 회원번호는 9~10자리 정수형. int 범위는 21억까지라 안전 마진이 부족해
 *   Long 으로 받아 User.kakaoId 와 타입 일치.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(Long id) {}
