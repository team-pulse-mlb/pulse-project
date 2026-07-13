package com.pulse.api.team;

import com.pulse.api.team.dto.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * 팀 관련 API 컨트롤러입니다.
 *
 * 현재 P1에서는 회원가입 Step 2의 관심팀 선택 화면에서 사용할
 * 전체 팀 목록 조회 API만 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    /*
     * 전체 MLB 팀 목록 조회 API.
     *
     * 사용 위치:
     * - 회원가입 Step 2 관심팀 선택
     * - 추후 마이페이지 관심팀 변경
     *
     * 로그인 전 회원가입 화면에서도 호출해야 하므로
     * SecurityConfig에서 permitAll 처리해야 합니다.
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getTeams() {
        return ResponseEntity.ok(teamService.getTeams());
    }
}