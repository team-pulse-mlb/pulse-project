package com.pulse.poller;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진행 중 경기의 play를 커서 기반으로 증분 수집한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaySyncService {

    /** 폴링 1회당 경기별 최대 페이지 수 (호출량 폭주 방지) */
    private static final int MAX_PAGES_PER_SYNC = 5;

    private final BalldontlieClient client;
    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final ScoreTaskPublisher publisher;

    @Transactional
    public void syncLiveGamePlays() {
        for (Game game : gameRepository.findByStatus(Game.STATUS_IN_PROGRESS)) {
            try {
                int saved = syncPlays(game);
                if (saved > 0) {
                    publisher.publish(game.getId());
                }
            } catch (Exception e) {
                // 한 경기 실패가 다른 경기 수집을 막지 않게 한다
                log.error("plays sync failed: gameId={}", game.getId(), e);
            }
        }
    }

    private int syncPlays(Game game) {
        int savedCount = 0;
        Long cursor = game.getPlaysCursor();
        Long lastOrder = null;

        for (int page = 0; page < MAX_PAGES_PER_SYNC; page++) {
            ListResponse<BdlPlay> response = client.getPlays(game.getId(), cursor);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }
            for (BdlPlay dto : response.data()) {
                if (dto.order() == null) {
                    continue;
                }
                lastOrder = lastOrder == null ? dto.order() : Math.max(lastOrder, dto.order());
                if (playRepository.existsByGameIdAndPlayOrder(game.getId(), dto.order())) {
                    continue;
                }
                playRepository.save(toEntity(game.getId(), dto));
                savedCount++;
            }
            if (response.nextCursor() == null) {
                // 마지막 페이지: next_cursor가 없으므로 마지막 play order를 커서로 사용해
                // 다음 폴링부터 그 이후 play만 받는다 (cursor는 order 기반 값)
                cursor = lastOrder != null ? lastOrder : cursor;
                break;
            }
            cursor = response.nextCursor();
        }

        if (cursor != null) {
            game.setPlaysCursor(cursor);
            gameRepository.save(game);
        }
        return savedCount;
    }

    private Play toEntity(long gameId, BdlPlay dto) {
        Play play = new Play();
        play.setGameId(gameId);
        play.setPlayOrder(dto.order());
        play.setType(dto.type());
        play.setInning(dto.inning());
        play.setInningType(dto.inningType());
        play.setText(dto.text());
        play.setHomeScore(dto.homeScore());
        play.setAwayScore(dto.awayScore());
        play.setScoringPlay(dto.scoringPlay());
        play.setScoreValue(dto.scoreValue());
        play.setOuts(dto.outs());
        play.setBalls(dto.balls());
        play.setStrikes(dto.strikes());
        play.setFetchedAt(Instant.now());
        return play;
    }
}
