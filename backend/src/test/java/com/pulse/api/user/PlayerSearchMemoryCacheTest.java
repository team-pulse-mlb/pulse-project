package com.pulse.api.user;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 선수 검색 인메모리 캐시의 동시성 동작을 검증합니다.
 */
class PlayerSearchMemoryCacheTest {

    /**
     * 같은 검색어가 동시에 두 번 요청되더라도
     * 실제 외부 loader는 한 번만 실행되는지 검증합니다.
     *
     * 첫 번째 요청:
     * - loader 실행
     * - 외부 응답을 기다리는 상태
     *
     * 두 번째 요청:
     * - 같은 검색어가 이미 실행 중임을 확인
     * - 새 loader를 실행하지 않고 첫 번째 Future를 기다림
     */
    @Test
    void getOrLoad_shouldExecuteLoaderOnceForConcurrentSameKeyword()
            throws Exception {

        // given
        PlayerSearchMemoryCache cache =
                new PlayerSearchMemoryCache();

        BdlPlayer player =
                new BdlPlayer(
                        208L,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        null
                );

        /*
         * 실제 loader가 몇 번 실행됐는지 기록합니다.
         */
        AtomicInteger loaderCallCount =
                new AtomicInteger();

        /*
         * 두 작업을 최대한 동시에 시작시키기 위한 문입니다.
         */
        CountDownLatch startGate =
                new CountDownLatch(1);

        /*
         * 첫 번째 loader가 실제로 실행됐는지 확인합니다.
         */
        CountDownLatch loaderStarted =
                new CountDownLatch(1);

        /*
         * 첫 번째 loader를 잠시 멈춰 두어,
         * 두 번째 요청이 실행 중인 Future를 발견하게 만듭니다.
         */
        CountDownLatch releaseLoader =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            /*
             * 두 요청이 공통으로 사용할 검색 작업입니다.
             */
            java.util.concurrent.Callable<List<BdlPlayer>> searchTask =
                    () -> {
                        /*
                         * 두 스레드가 모두 준비된 뒤
                         * 같은 시점에 검색을 시작합니다.
                         */
                        startGate.await(
                                2,
                                TimeUnit.SECONDS
                        );

                        return cache.getOrLoad(
                                "ohtani",
                                () -> {
                                    loaderCallCount.incrementAndGet();
                                    loaderStarted.countDown();

                                    try {
                                        boolean released =
                                                releaseLoader.await(
                                                        2,
                                                        TimeUnit.SECONDS
                                                );

                                        if (!released) {
                                            throw new IllegalStateException(
                                                    "테스트 loader 대기 시간이 초과됐습니다."
                                            );
                                        }
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();

                                        throw new RuntimeException(
                                                exception
                                        );
                                    }

                                    return List.of(player);
                                }
                        );
                    };

            Future<List<BdlPlayer>> firstFuture =
                    executor.submit(searchTask);

            Future<List<BdlPlayer>> secondFuture =
                    executor.submit(searchTask);

            /*
             * 두 검색 요청을 동시에 출발시킵니다.
             */
            startGate.countDown();

            /*
             * 적어도 하나의 요청이 loader에 진입했는지 확인합니다.
             */
            assertTrue(
                    loaderStarted.await(
                            2,
                            TimeUnit.SECONDS
                    )
            );

            /*
             * 두 번째 요청이 getOrLoad()에 진입해서
             * 기존 Future를 확인할 시간을 잠깐 줍니다.
             */
            Thread.sleep(100);

            /*
             * 첫 번째 loader가 결과를 반환하도록 해제합니다.
             */
            releaseLoader.countDown();

            List<BdlPlayer> firstResult =
                    firstFuture.get(
                            2,
                            TimeUnit.SECONDS
                    );

            List<BdlPlayer> secondResult =
                    secondFuture.get(
                            2,
                            TimeUnit.SECONDS
                    );

            // then
            /*
             * 동시에 두 번 요청했지만
             * 실제 loader는 한 번만 실행돼야 합니다.
             */
            assertEquals(
                    1,
                    loaderCallCount.get()
            );

            /*
             * 두 요청 모두 같은 선수 검색 결과를 받아야 합니다.
             */
            assertEquals(
                    List.of(player),
                    firstResult
            );

            assertEquals(
                    List.of(player),
                    secondResult
            );

        } finally {
            /*
             * 테스트가 실패해도 스레드 풀이 남지 않도록 종료합니다.
             */
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }
}