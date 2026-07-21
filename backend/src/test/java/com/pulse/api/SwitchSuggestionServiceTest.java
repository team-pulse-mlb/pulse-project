package com.pulse.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SwitchSuggestionServiceTest {

    @Mock
    private RankingService rankingService;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private WatchScoreRepository watchScoreRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SwitchSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new SwitchSuggestionService(
                rankingService,
                gameRepository,
                watchScoreRepository,
                memberRepository,
                userSettingRepository,
                redisTemplate
        );
    }

    @Test
    void 후보가_없으면_추천하지_않는다() {
        when(rankingService.findSwitchCandidate(101L))
                .thenReturn(OptionalLong.empty());

        assertNull(service.findSuggestion(101L, null));

        verifyNoInteractions(
                gameRepository,
                watchScoreRepository,
                redisTemplate
        );
    }

    @Test
    void 전환_추천을_끈_회원에게는_추천하지_않는다() {
        Member member = member(1L);

        when(memberRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(1L))
                .thenReturn(Optional.of(
                        UserSetting.create(
                                member,
                                true,
                                true,
                                false
                        )
                ));

        assertNull(
                service.findSuggestion(
                        101L,
                        "user@test.com"
                )
        );

        verifyNoInteractions(rankingService);
    }

    @Test
    void 비로그인_사용자에게_후보와_서버_문구를_반환한다() {
        Game candidate = liveGame(202L);
        WatchScore score = new WatchScore();
        score.setTags(List.of(
                "접전 흐름",
                "",
                "득점권 압박"
        ));

        when(rankingService.findSwitchCandidate(101L))
                .thenReturn(OptionalLong.of(202L));
        when(gameRepository.findById(202L))
                .thenReturn(Optional.of(candidate));
        when(watchScoreRepository
                .findTopByGameIdOrderByComputedAtDesc(202L))
                .thenReturn(Optional.of(score));

        GameQueryService.SwitchSuggestionResponse result =
                service.findSuggestion(101L, null);

        assertNotNull(result);
        assertEquals(202L, result.gameId());
        assertEquals("Texas Rangers", result.matchup().home());
        assertEquals("Detroit Tigers", result.matchup().away());
        assertEquals("득점권 압박", result.latestTag());
        assertEquals(
                "지금은 다른 경기가 더 볼 만해요. <득점권 압박>",
                result.message()
        );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void 같은_회원과_후보의_쿨다운이_남아있으면_추천하지_않는다() {
        Member member = member(1L);

        when(memberRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(member));
        when(userSettingRepository.findById(1L))
                .thenReturn(Optional.empty());
        when(rankingService.findSwitchCandidate(101L))
                .thenReturn(OptionalLong.of(202L));
        when(gameRepository.findById(202L))
                .thenReturn(Optional.of(liveGame(202L)));
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "game:switch:suggestion:1:202",
                "1",
                Duration.ofMinutes(15)
        )).thenReturn(false);

        assertNull(
                service.findSuggestion(
                        101L,
                        "user@test.com"
                )
        );

        verifyNoInteractions(watchScoreRepository);
    }

    private Member member(long userId) {
        return Member.builder()
                .userId(userId)
                .email("user@test.com")
                .passwordHash("encoded-password")
                .build();
    }

    private Game liveGame(long gameId) {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setHomeTeamName("Texas Rangers");
        game.setAwayTeamName("Detroit Tigers");
        return game;
    }
}
