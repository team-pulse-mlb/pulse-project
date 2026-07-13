package com.pulse.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pulse.api.notification.dto.NotificationReadRequest;
import com.pulse.api.notification.dto.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NotificationController의 HTTP 요청·응답을 확인하는 테스트입니다.
 *
 * 실제 DB나 Spring 전체 애플리케이션을 실행하지 않고,
 * MockMvc로 Controller만 독립적으로 테스트합니다.
 *
 * 확인 대상:
 * 1. GET /api/me/notifications
 * 2. POST /api/me/notifications/read - 선택 읽음
 * 3. POST /api/me/notifications/read - 전체 읽음
 * 4. 잘못된 알림 ID 요청 검증
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    /**
     * Controller가 호출할 NotificationService의 가짜 객체입니다.
     */
    @Mock
    private NotificationService notificationService;

    /**
     * Mock Service가 주입된 실제 Controller입니다.
     */
    @InjectMocks
    private NotificationController notificationController;

    /**
     * Controller에 가짜 HTTP 요청을 전달하는 도구입니다.
     */
    private MockMvc mockMvc;

    /**
     * 요청 DTO를 JSON 문자열로 바꾸는 도구입니다.
     */
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        /*
         * Instant를 JSON 문자열로 정상 변환하기 위해
         * JavaTimeModule을 등록합니다.
         */
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        /*
         * Instant를 숫자 타임스탬프가 아니라
         * "2026-07-13T03:00:00Z" 형태로 반환하도록 설정합니다.
         */
        objectMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );

        /*
         * @Valid, @Positive 등 Jakarta Validation을
         * MockMvc 독립 테스트에서도 실행하도록 Validator를 설정합니다.
         */
        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        /*
         * Spring 전체 Context를 띄우지 않고
         * NotificationController만 MockMvc에 등록합니다.
         */
        mockMvc = MockMvcBuilders
                .standaloneSetup(notificationController)
                .setValidator(validator)
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(
                                objectMapper
                        )
                )
                .build();
    }

    /**
     * GET /api/me/notifications 요청 시
     * 로그인 사용자의 이메일로 Service를 호출하고
     * 알림 목록을 JSON으로 반환하는지 확인합니다.
     */
    @Test
    void getMyNotificationsReturnsNotificationList()
            throws Exception {

        // given
        Instant createdAt = Instant.parse(
                "2026-07-13T03:00:00Z"
        );

        NotificationResponse response =
                new NotificationResponse(
                        100L,
                        "SURGE",
                        2000L,
                        "경기 흐름이 급변하고 있어요.",
                        false,
                        null,
                        createdAt
                );

        when(notificationService.getMyNotifications(
                "user@example.com"
        )).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(
                        get("/api/me/notifications")

                                /*
                                 * JWT 인증이 끝난 상황을 가정하여
                                 * Authentication 객체를 요청에 넣습니다.
                                 */
                                .principal(authentication())
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].notificationId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value("SURGE")
                )
                .andExpect(
                        jsonPath("$[0].gameId")
                                .value(2000)
                )
                .andExpect(
                        jsonPath("$[0].message")
                                .value("경기 흐름이 급변하고 있어요.")
                )
                .andExpect(
                        jsonPath("$[0].read")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$[0].readAt")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].createdAt")
                                .value("2026-07-13T03:00:00Z")
                );

        /*
         * Controller가 인증 사용자의 이메일을
         * Service에 제대로 전달했는지 확인합니다.
         */
        verify(notificationService)
                .getMyNotifications("user@example.com");
    }

    /**
     * all=false인 경우 전달된 알림 ID만
     * 선택 읽음 처리하는지 확인합니다.
     */
    @Test
    void markAsReadUpdatesSelectedNotifications()
            throws Exception {

        // given
        NotificationReadRequest request =
                new NotificationReadRequest(
                        false,
                        List.of(10L, 11L)
                );

        when(notificationService.markSelectedAsRead(
                "user@example.com",
                List.of(10L, 11L)
        )).thenReturn(2);

        // when & then
        mockMvc.perform(
                        post("/api/me/notifications/read")
                                .principal(authentication())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.updatedCount")
                                .value(2)
                );

        verify(notificationService).markSelectedAsRead(
                "user@example.com",
                List.of(10L, 11L)
        );

        /*
         * 선택 읽음 요청이므로 전체 읽음 메서드는
         * 호출되지 않아야 합니다.
         */
        verify(notificationService, never())
                .markAllAsRead("user@example.com");
    }

    /**
     * all=true인 경우 notificationIds 내용과 관계없이
     * 현재 사용자의 모든 미읽음 알림을 처리하는지 확인합니다.
     */
    @Test
    void markAsReadUpdatesAllNotifications()
            throws Exception {

        // given
        NotificationReadRequest request =
                new NotificationReadRequest(
                        true,
                        List.of(10L, 11L)
                );

        when(notificationService.markAllAsRead(
                "user@example.com"
        )).thenReturn(5);

        // when & then
        mockMvc.perform(
                        post("/api/me/notifications/read")
                                .principal(authentication())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.updatedCount")
                                .value(5)
                );

        verify(notificationService)
                .markAllAsRead("user@example.com");

        /*
         * all=true이므로 notificationIds는 무시되고
         * 선택 읽음 메서드는 호출되지 않아야 합니다.
         */
        verify(notificationService, never())
                .markSelectedAsRead(
                        "user@example.com",
                        List.of(10L, 11L)
                );
    }

    /**
     * 알림 ID는 1 이상의 양수여야 합니다.
     *
     * 0이 포함된 요청은 @Positive 검증에 실패하여
     * Controller 로직에 진입하기 전에 400 응답이 발생해야 합니다.
     */
    @Test
    void markAsReadReturnsBadRequestForInvalidId()
            throws Exception {

        // given
        NotificationReadRequest request =
                new NotificationReadRequest(
                        false,
                        List.of(0L)
                );

        // when & then
        mockMvc.perform(
                        post("/api/me/notifications/read")
                                .principal(authentication())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        /*
         * 요청 검증에서 차단됐으므로
         * Service는 호출되지 않아야 합니다.
         */
        verifyNoInteractions(notificationService);
    }

    /**
     * JWT 인증을 통과한 사용자를 표현하는
     * Authentication 객체를 생성합니다.
     *
     * 실제 애플리케이션에서는 JwtAuthenticationFilter가
     * 이 객체를 SecurityContext에 등록합니다.
     */
    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of()
        );
    }
}