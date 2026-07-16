package com.pulse.api.user;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 PostgreSQL에서 PlayerRegistrationWriter의
 * 원자적 upsert 동작을 검증하는 통합 테스트입니다.
 *
 * Mockito 단위 테스트와 달리 실제 players 테이블에
 * INSERT ... ON CONFLICT 쿼리를 실행합니다.
 */
@Testcontainers
@DataJpaTest(
        properties = {
                /*
                 * 테스트 컨테이너 시작 시 Hibernate가 테이블을 생성합니다.
                 *
                 * 컨테이너 자체가 테스트 종료 후 폐기되므로
                 * create-drop처럼 종료 시 테이블 삭제를 시도할 필요가 없습니다.
                 *
                 * create-drop을 사용하면 PostgreSQL 컨테이너가 먼저 종료된 뒤
                 * Hibernate가 DROP을 시도하면서 연결 종료 경고가 발생할 수 있습니다.
                 */
                "spring.jpa.hibernate.ddl-auto=create",

                /*
                 * 현재 프로젝트의 기본 설정과 동일하게
                 * 통합 테스트에서도 Flyway를 실행하지 않습니다.
                 */
                "spring.flyway.enabled=false"
        }
)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(PlayerRegistrationWriter.class)

/*
 * @DataJpaTest는 기본적으로 테스트 메서드 전체를
 * 하나의 트랜잭션으로 감쌉니다.
 *
 * 그러나 이번 테스트에서는 두 작업 스레드가
 * 각각 독립적인 Writer 트랜잭션을 시작해야 하므로
 * 테스트 메서드 자체의 트랜잭션은 비활성화합니다.
 */
@Transactional(
        propagation = Propagation.NOT_SUPPORTED
)
class PlayerRegistrationWriterIntegrationTest {

    /**
     * 실제 개발 환경과 동일한 PostgreSQL 16을 사용합니다.
     *
     * static 필드이므로 이 테스트 클래스 동안
     * 컨테이너 한 개를 재사용합니다.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:16-alpine"
            );

    @Autowired
    private PlayerRegistrationWriter playerRegistrationWriter;

    @Autowired
    private PlayerRepository playerRepository;

    /**
     * 각 테스트가 끝날 때 players 데이터를 정리합니다.
     */
    @AfterEach
    void cleanUp() {
        playerRepository.deleteAll();
    }

    /**
     * 서로 다른 두 트랜잭션이 같은 player_id를 동시에 등록하더라도
     * PK 중복 오류 없이 두 작업이 모두 완료되고,
     * players 테이블에는 행이 하나만 남는지 검증합니다.
     */
    @Test
    void concurrentRegistrationOfSamePlayer_shouldCreateOnlyOneRow()
            throws Exception {

        // given
        Long playerId = 9_000_001L;

        Instant observedAt =
                Instant.parse(
                        "2026-07-16T00:00:00Z"
                );

        /*
         * 두 스레드 모두 동일한 선수를 등록합니다.
         *
         * team=null이므로 teams 테이블에
         * 별도 테스트 데이터를 넣을 필요가 없습니다.
         */
        BdlPlayer playerDto =
                new BdlPlayer(
                        playerId,
                        "Concurrent Test Player",
                        "Concurrent",
                        "Player",
                        "P",
                        null
                );

        /*
         * 두 작업 스레드가 모두 준비될 때까지 기다리는 latch입니다.
         */
        CountDownLatch readyLatch =
                new CountDownLatch(2);

        /*
         * 두 작업이 최대한 같은 시점에 DB 쿼리를 시작하도록
         * 출발 신호를 제어하는 latch입니다.
         */
        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        /*
         * 동일한 Writer 메서드를 서로 다른 스레드에서 실행합니다.
         *
         * PlayerRegistrationWriter.upsertPlayers()에
         * @Transactional이 있으므로 각 스레드는
         * 서로 독립된 트랜잭션을 사용합니다.
         */
        Callable<List<Player>> registrationTask =
                () -> {
                    readyLatch.countDown();

                    boolean started =
                            startLatch.await(
                                    5,
                                    TimeUnit.SECONDS
                            );

                    if (!started) {
                        throw new IllegalStateException(
                                "동시 등록 테스트 시작 신호를 기다리다 시간 초과되었습니다."
                        );
                    }

                    return playerRegistrationWriter
                            .upsertPlayers(
                                    List.of(playerDto),
                                    observedAt
                            );
                };

        try {
            // when
            Future<List<Player>> firstFuture =
                    executorService.submit(
                            registrationTask
                    );

            Future<List<Player>> secondFuture =
                    executorService.submit(
                            registrationTask
                    );

            /*
             * 두 스레드가 실제 실행 준비를 마쳤는지 확인한 뒤
             * 동시에 출발시킵니다.
             */
            assertTrue(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )
            );

            startLatch.countDown();

            /*
             * 내부에서 중복 PK 오류 등이 발생하면
             * Future.get()에서 예외가 발생해 테스트가 실패합니다.
             */
            List<Player> firstResult =
                    firstFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<Player> secondResult =
                    secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            // then
            assertEquals(
                    1,
                    firstResult.size()
            );

            assertEquals(
                    1,
                    secondResult.size()
            );

            /*
             * 두 번의 동시 등록이 실행됐지만
             * 동일한 player_id 행은 하나만 존재해야 합니다.
             */
            assertEquals(
                    1L,
                    playerRepository.count()
            );

            Player savedPlayer =
                    playerRepository
                            .findById(playerId)
                            .orElseThrow();

            assertEquals(
                    playerId,
                    savedPlayer.getId()
            );

            assertEquals(
                    "Concurrent Test Player",
                    savedPlayer.getFullName()
            );

            assertEquals(
                    "Concurrent",
                    savedPlayer.getFirstName()
            );

            assertEquals(
                    "Player",
                    savedPlayer.getLastName()
            );

            assertEquals(
                    "P",
                    savedPlayer.getPosition()
            );

            assertEquals(
                    observedAt,
                    savedPlayer.getCreatedAt()
            );

            assertEquals(
                    observedAt,
                    savedPlayer.getUpdatedAt()
            );

        } finally {
            /*
             * 테스트 성공·실패와 관계없이
             * 작업 스레드를 종료합니다.
             */
            executorService.shutdownNow();
        }
    }
}