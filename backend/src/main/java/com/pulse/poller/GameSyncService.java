package com.pulse.poller;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /games tripwire. 전 경기를 싸게 감시하고, 상태·점수 변화가 감지된 경기의
 * 점수 재계산을 요청한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameSyncService {

    private final BalldontlieClient client;
    private final GameRepository gameRepository;
    private final ScoreTaskPublisher publisher;

    /**
     * 오늘(UTC)과 어제(UTC) 경기를 동기화한다.
     * MLB 경기가 UTC 날짜 경계를 넘는 경우가 있어 두 날짜를 함께 조회한다.
     */
    @Transactional
    public void syncTodayGames() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<BdlGame> fetched = new ArrayList<>();
        fetched.addAll(client.getGames(today.minusDays(1)));
        fetched.addAll(client.getGames(today));

        for (BdlGame dto : fetched) {
            upsert(dto);
        }
        log.debug("games synced: {}", fetched.size());
    }

    private void upsert(BdlGame dto) {
        Game game = gameRepository.findById(dto.id()).orElseGet(() -> {
            Game created = new Game();
            created.setId(dto.id());
            return created;
        });

        boolean changed = game.getStatus() == null
                || !game.getStatus().equals(dto.status())
                || !java.util.Objects.equals(game.getHomeRuns(), runs(dto.homeTeamData()))
                || !java.util.Objects.equals(game.getAwayRuns(), runs(dto.awayTeamData()))
                || !java.util.Objects.equals(game.getPeriod(), dto.period());

        game.setStatus(dto.status());
        game.setPeriod(dto.period());
        if (dto.date() != null) {
            game.setStartTime(Instant.parse(dto.date()));
        }
        if (dto.homeTeam() != null) {
            game.setHomeTeamId(dto.homeTeam().id());
            game.setHomeTeamName(dto.homeTeam().displayName());
            game.setHomeTeamAbbr(dto.homeTeam().abbreviation());
        }
        if (dto.awayTeam() != null) {
            game.setAwayTeamId(dto.awayTeam().id());
            game.setAwayTeamName(dto.awayTeam().displayName());
            game.setAwayTeamAbbr(dto.awayTeam().abbreviation());
        }
        if (dto.homeTeamData() != null) {
            game.setHomeRuns(dto.homeTeamData().runs());
            game.setHomeInningScores(dto.homeTeamData().inningScores());
        }
        if (dto.awayTeamData() != null) {
            game.setAwayRuns(dto.awayTeamData().runs());
            game.setAwayInningScores(dto.awayTeamData().inningScores());
        }
        game.setUpdatedAt(Instant.now());
        gameRepository.save(game);

        // 상태·점수·이닝이 바뀐 경기만 재계산 요청 (종료 전환 포함 — scorer가 랭킹에서 제거)
        if (changed && (game.isLive() || game.isFinal())) {
            publisher.publish(game.getId());
        }
    }

    private Integer runs(BdlGame.TeamData data) {
        return data == null ? null : data.runs();
    }
}
