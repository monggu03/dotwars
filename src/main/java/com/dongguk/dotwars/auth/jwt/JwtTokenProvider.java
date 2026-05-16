package com.dongguk.dotwars.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 토큰 생성 / 검증 / 추출.
 *
 * 정책:
 *  - 알고리즘 HS256 (HMAC + SHA-256). 256 bit 이상 시크릿 필요 → 우리는 base64 인코딩된 32 byte 키.
 *  - 만료 7일 (application.yml 의 jwt.access-token-validity-seconds 로 주입).
 *  - subject 에는 user.id (Long) 만 저장. 부가 정보(닉네임 등)는 토큰에 넣지 않고 매번 DB 조회.
 *    토큰 페이로드에 정보를 많이 넣으면 정보 변경 시 즉시 반영되지 않는 stale 문제 + 토큰 비대화.
 *
 * jjwt 0.12.x API:
 *  - Jwts.builder()  ... .signWith(SecretKey, Jwts.SIG.HS256).compact()
 *  - Jwts.parser().verifyWith(SecretKey).build().parseSignedClaims(token).getPayload()
 *  - 0.11.x 의 parserBuilder() / setSigningKey() / parseClaimsJws() 는 deprecated. 검색 시 버전 주의.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValiditySeconds;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${jwt.issuer}") String issuer
    ) {
        // 시크릿은 base64 인코딩된 상태로 yml 에 들어옴 → 디코드해서 raw byte 배열로 변환.
        // hmacShaKeyFor 는 32 byte(256 bit) 미만이면 예외 → 약한 키 실수를 컴파일 직후 즉시 발견.
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.issuer = issuer;
    }

    /**
     * 토큰 생성.
     * @param userId 사용자 PK (subject 에 String 화해서 저장)
     */
    public String createToken(Long userId) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .issuer(issuer)                                  // iss — 토큰 발급자(우리 서비스 이름)
                .subject(String.valueOf(userId))                  // sub — 토큰의 주체(사용자)
                .issuedAt(Date.from(now))                         // iat — 발급 시각
                .expiration(Date.from(expiration))                // exp — 만료 시각
                .signWith(secretKey, Jwts.SIG.HS256)              // 서명
                .compact();
    }

    /**
     * 토큰에서 user.id 추출.
     * 호출 전 validateToken 으로 검증을 통과한 토큰에서만 사용한다고 가정.
     * (검증 안 한 토큰에 이걸 직접 쓰면 JwtException 으로 전파됨)
     */
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰 유효성 검증.
     * 서명 불일치, 만료, 형식 오류 등 어떤 이유든 실패 시 false 반환.
     * 호출자(인증 필터)에서 false 면 SecurityContext 채우지 않고 다음 필터로 흘려보냄 → 미인증 처리.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 토큰 값 자체는 절대 로그에 출력하지 않음 (탈취 위험).
            log.debug("[jwt] 검증 실패 reason={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
