package com.dongguk.dotwars.user.dto;

/**
 * 응답 안의 진영 요약 정보.
 *
 * colorHex 는 "#FF7F0E" 형태로 그대로 내려보내 클라이언트가 CSS 변수에 주입.
 * 클라가 색 매핑 테이블을 갖지 않아도 되도록 서버가 진실원천.
 */
public record FactionInfo(Long id, String name, String colorHex) {}
