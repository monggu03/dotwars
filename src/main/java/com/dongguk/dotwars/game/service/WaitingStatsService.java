package com.dongguk.dotwars.game.service;

import com.dongguk.dotwars.department.domain.Faction;
import com.dongguk.dotwars.department.repository.FactionRepository;
import com.dongguk.dotwars.game.dto.WaitingStatsResponse;
import com.dongguk.dotwars.game.dto.WaitingStatsResponse.FactionWaiting;
import com.dongguk.dotwars.user.repository.UserRepository;
import com.dongguk.dotwars.user.repository.projection.FactionParticipantCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대기화면 인원 통계 조립 — 진영 마스터(이름/색/순서) + 진영별 가입자 수.
 *
 * 왜 별도 서비스: 진영 마스터(FactionRepository)와 가입자 집계(UserRepository) 두 출처를
 * 합치는 로직이라, 컨트롤러에 두지 않고 한 곳에 모음. 시작 전 폴링이라 호출 빈도도 낮음.
 */
@Service
@RequiredArgsConstructor
public class WaitingStatsService {

    private final FactionRepository factionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WaitingStatsResponse getWaitingStats() {
        // 진영별 가입자 수 → Map (가입자 0인 진영은 결과에 없으므로 getOrDefault 로 0 처리)
        Map<Long, Long> countByFaction = new HashMap<>();
        for (FactionParticipantCount row : userRepository.countParticipantsByFaction()) {
            countByFaction.put(row.getFactionId(), row.getCnt());
        }

        // 진영 마스터를 displayOrder(인문→사회→자연→공학→예술) 로 정렬해 그대로 내려줌
        List<Faction> factions = factionRepository.findAll();
        factions.sort(Comparator.comparingInt(Faction::getDisplayOrder));

        long total = 0;
        List<FactionWaiting> list = new java.util.ArrayList<>(factions.size());
        for (Faction f : factions) {
            long c = countByFaction.getOrDefault(f.getId(), 0L);
            total += c;
            list.add(new FactionWaiting(f.getId(), f.getName(), f.getColorHex(), c));
        }
        return new WaitingStatsResponse(total, list);
    }
}
