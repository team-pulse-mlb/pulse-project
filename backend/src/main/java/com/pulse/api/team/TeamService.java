package com.pulse.api.team;

import com.pulse.domain.TeamRepository;
import com.pulse.api.team.dto.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pulse.domain.Team;

import java.util.Comparator;
import java.util.List;

/*
 * 팀 목록 조회 비즈니스 로직을 담당하는 서비스입니다.
 *
 * 현재 P1에서는:
 * - 관심팀 선택 화면에 보여줄 MLB 전체 팀 목록 조회
 *
 * 나중에 P2에서는:
 * - 활성 팀만 조회
 * - 리그/지구별 필터
 * - 팀 검색
 * 같은 기능을 여기서 확장할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    /*
     * 관심팀 선택 화면에 보여줄 전체 팀 목록을 조회합니다.
     *
     * 정렬 기준:
     * 1. league
     * 2. division
     * 3. displayName
     *
     * Comparator.comparing()에서 Team 타입을 명시한 이유:
     * IntelliJ나 Java 컴파일러가 람다의 team 타입을 제대로 추론하지 못하는 경우가 있어서
     * (Team team) 형태로 타입을 직접 적어 안정적으로 처리합니다.
     */
    public List<TeamResponse> getTeams() {
        return teamRepository.findAll()
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        (Team team) -> nullToEmpty(team.getLeague())
                                )
                                .thenComparing(
                                        (Team team) -> nullToEmpty(team.getDivision())
                                )
                                .thenComparing(
                                        (Team team) -> nullToEmpty(team.getDisplayName())
                                )
                )
                .map(TeamResponse::from)
                .toList();
    }

    /*
     * 정렬 중 null 값 때문에 NullPointerException이 발생하지 않도록
     * null을 빈 문자열로 바꿔주는 보조 메서드입니다.
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}