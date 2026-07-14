package com.pulse.api.sse;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SseTokenService 단위 테스트입니다.
 *
 * 실제 Redis를 실행하지 않고 Mockito로 Redis 동작을 대신합니다.
 */
@ExtendWith(MockitoExtension.class)
class SseTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MemberRepository memberRepository;

    /**
     * @InjectMocks를 사용하지 않고 직접 생성하는 이유:
     *
     * 테스트 대상 생성 방식이 명확하고,
     * 내부 SecureRandom은 서비스가 직접 생성하도록 두기 위함입니다.
     */
    private SseTokenService createService() {
        return new SseTokenService(
                redisTemplate,
                memberRepository
        );
    }

    @Test
    void issue_shouldStoreUserIdWithSixtySecondTtl() {
        // given
        Member member =
                mock(Member.class);

        when(memberRepository.findByEmail(
                "user@example.com"
        )).thenReturn(
                Optional.of(member)
        );

        when(member.getUserId())
                .thenReturn(7L);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                anyString(),
                eq("7"),
                eq(Duration.ofSeconds(60))
        )).thenReturn(true);

        SseTokenService service =
                createService();

        // when
        String token =
                service.issue(
                        " USER@EXAMPLE.COM "
                );

        // then
        assertThat(token)
                .isNotBlank();

        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(valueOperations).setIfAbsent(
                keyCaptor.capture(),
                eq("7"),
                eq(Duration.ofSeconds(60))
        );

        /*
         * 반환된 토큰이 실제 Redis 키에 그대로 사용됐는지 확인합니다.
         */
        assertThat(keyCaptor.getValue())
                .isEqualTo(
                        "sse:token:" + token
                );

        verify(memberRepository)
                .findByEmail("user@example.com");
    }

    @Test
    void consumeUserId_shouldReturnAndDeleteStoredUserId() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.getAndDelete(
                "sse:token:test-token"
        )).thenReturn("7");

        SseTokenService service =
                createService();

        // when
        OptionalLong userId =
                service.consumeUserId(
                        "test-token"
                );

        // then
        assertThat(userId)
                .isPresent();

        assertThat(userId.getAsLong())
                .isEqualTo(7L);

        verify(valueOperations)
                .getAndDelete(
                        "sse:token:test-token"
                );
    }

    @Test
    void consumeUserId_shouldAllowTokenOnlyOnce() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        /*
         * 첫 번째 사용에서는 userId가 반환되고,
         * 두 번째 사용에서는 이미 삭제돼 null이 반환됩니다.
         */
        when(valueOperations.getAndDelete(
                "sse:token:one-time-token"
        ))
                .thenReturn("7")
                .thenReturn((String) null);

        SseTokenService service =
                createService();

        // when
        OptionalLong firstResult =
                service.consumeUserId(
                        "one-time-token"
                );

        OptionalLong secondResult =
                service.consumeUserId(
                        "one-time-token"
                );

        // then
        assertThat(firstResult)
                .hasValue(7L);

        assertThat(secondResult)
                .isEmpty();

        verify(valueOperations, times(2))
                .getAndDelete(
                        "sse:token:one-time-token"
                );
    }

    @Test
    void consumeUserId_shouldReturnEmptyForBlankToken() {
        // given
        SseTokenService service =
                createService();

        // when
        OptionalLong result =
                service.consumeUserId(" ");

        // then
        assertThat(result)
                .isEmpty();

        /*
         * 빈 토큰은 Redis에 조회할 필요가 없습니다.
         */
        verifyNoInteractions(
                redisTemplate,
                memberRepository
        );
    }

    @Test
    void issue_shouldThrowWhenMemberDoesNotExist() {
        // given
        when(memberRepository.findByEmail(
                "missing@example.com"
        )).thenReturn(
                Optional.empty()
        );

        SseTokenService service =
                createService();

        // when & then
        assertThatThrownBy(() ->
                service.issue(
                        "missing@example.com"
                )
        )
                .isInstanceOf(
                        UsernameNotFoundException.class
                )
                .hasMessage(
                        "가입되지 않은 이메일입니다."
                );

        verifyNoInteractions(redisTemplate);
    }
}