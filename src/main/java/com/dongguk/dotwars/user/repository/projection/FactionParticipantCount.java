package com.dongguk.dotwars.user.repository.projection;

/**
 * 진영별 가입자 수 집계 결과 (Spring Data 인터페이스 프로젝션).
 * UserRepository.countParticipantsByFaction() 의 SELECT 별칭(factionId, cnt)과 이름이 일치해야 매핑됨.
 */
public interface FactionParticipantCount {
    Long getFactionId();
    long getCnt();
}
