package com.pulse.api.gamedetail;

import com.pulse.api.gamedetail.GameQueryService.MatchupResponse;
import com.pulse.api.gamedetail.GameQueryService.SwitchSuggestionResponse;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class SwitchSuggestionService {

    private static final Duration COOLDOWN =
            Duration.ofMinutes(15);

    private static final String COOLDOWN_KEY_PREFIX =
            "game:switch:suggestion:";

    private final RankingService rankingService;
    private final GameRepository gameRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final MemberRepository memberRepository;
    private final UserSettingRepository userSettingRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 현재 경기보다 관전 가치가 높은 다른 LIVE 경기를 추천한다.
     *
     * 저장 알림이 아니므로 user_notifications에는 기록하지 않는다.
     * 로그인 사용자는 설정과 Redis 쿨다운을 적용하고,
     * 비로그인 사용자의 중복 제한은 프론트에서 보조 처리한다.
     */
    public SwitchSuggestionResponse findSuggestion(
            long currentGameId,
            String username
    ) {
        Member member = findMember(username);

        if (member != null && !isEnabled(member)) {
            return null;
        }

        OptionalLong candidateId =
                rankingService.findSwitchCandidate(currentGameId);

        if (candidateId.isEmpty()) {
            return null;
        }

        Game candidate = gameRepository
                .findById(candidateId.getAsLong())
                .filter(game ->
                        Game.STATUS_IN_PROGRESS.equals(
                                game.getStatus()
                        )
                )
                .orElse(null);

        if (candidate == null) {
            return null;
        }

        if (
                member != null
                && !reserveCooldown(
                        member.getUserId(),
                        candidate.getId()
                )
        ) {
            return null;
        }

        String latestTag =
                latestTag(candidate.getId());

        return new SwitchSuggestionResponse(
                candidate.getId(),
                new MatchupResponse(
                        candidate.getHomeTeamName(),
                        candidate.getAwayTeamName()
                ),
                latestTag,
                suggestionMessage(latestTag)
        );
    }

    private Member findMember(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        return memberRepository
                .findByEmail(username)
                .orElse(null);
    }

    private boolean isEnabled(Member member) {
        return userSettingRepository
                .findById(member.getUserId())
                .map(UserSetting::isGameSwitchAlert)
                .orElse(true);
    }

    private boolean reserveCooldown(
            long userId,
            long candidateGameId
    ) {
        String key =
                COOLDOWN_KEY_PREFIX
                        + userId
                        + ":"
                        + candidateGameId;

        return Boolean.TRUE.equals(
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                key,
                                "1",
                                COOLDOWN
                        )
        );
    }

    private String suggestionMessage(
            String latestTag
    ) {
        if (
                latestTag == null
                || latestTag.isBlank()
        ) {
            return "지금은 다른 경기가 더 볼 만해요.";
        }

        return "지금은 다른 경기가 더 볼 만해요. <"
                + latestTag + ">";
    }

    private String latestTag(long gameId) {
        return watchScoreRepository
                .findTopByGameIdOrderByComputedAtDesc(gameId)
                .map(WatchScore::getTags)
                .filter(tags -> !tags.isEmpty())
                .map(this::lastNonBlankTag)
                .orElse(null);
    }

    private String lastNonBlankTag(List<String> tags) {
        for (int index = tags.size() - 1; index >= 0; index--) {
            String tag = tags.get(index);

            if (tag != null && !tag.isBlank()) {
                return tag;
            }
        }

        return null;
    }
}
