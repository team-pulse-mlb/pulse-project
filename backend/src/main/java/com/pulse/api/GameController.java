package com.pulse.api;

import com.pulse.api.GameEventQueryService.GameEventsResponse;
import com.pulse.api.GameQueryService.GameDetailView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameQueryService gameQueryService;
    private final GameEventQueryService gameEventQueryService;
    private final GameRecentPlayQueryService gameRecentPlayQueryService;

    @Operation(
            summary = "경기 상세 조회",
            description = """
                    경기 상태와 표시 모드에 맞는 상세 정보를 조회한다.
                    보호 모드에서는 점수와 결과 방향을 드러내는 필드를 제외한다.
                    """
    )
    @GetMapping("/{gameId}")
    public GameDetailView detail(
            @Parameter(
                    description = "balldontlie 경기 ID",
                    example = "5059041"
            )
            @PathVariable long gameId,

            /*
             * enum을 직접 바인딩하지 않고 문자열로 받아
             * 소문자 요청과 잘못된 요청을 서비스에서 안전하게 처리한다.
             */
            @Parameter(
                    description = """
                            protected는 스포일러 값을 숨기고,
                            revealed는 점수와 공개 정보를 포함한다.
                            """
            )
            @RequestParam(defaultValue = "protected")
            String mode
    ) {
        return gameQueryService.getGameDetail(
                gameId,
                mode
        );
    }

    /**
     * 경기 상세 이벤트 타임라인을 조회한다.
     *
     * 이벤트를 기본 상세 응답에 섞지 않고,
     * 별도의 /events API를 단일 원천으로 사용한다.
     */
    @Operation(
            summary = "경기 상세 이벤트 조회",
            description = """
                    보호 모드에서는 스포일러 안전 이벤트만 반환한다.
                    공개 모드에서는 보호 이벤트와 공개 전용 이벤트를 함께 반환한다.
                    """
    )
    @GetMapping("/{gameId}/events")
    public GameEventsResponse events(
            @Parameter(
                    description = "balldontlie 경기 ID",
                    example = "5059041"
            )
            @PathVariable long gameId,

            @Parameter(
                    description = """
                            protected는 초/말, 선수 이름, 결과 이벤트를 숨기고,
                            revealed는 공개 가능한 이벤트 정보를 포함한다.
                            """
            )
            @RequestParam(defaultValue = "protected")
            String mode
    ) {
        return gameEventQueryService.getEvents(
                gameId,
                mode
        );
    }
    /**
     * 경기 상세 화면의 최근 플레이를 조회한다.
     *
     * 최근 플레이에는 점수와 실제 플레이 문구가 포함되므로
     * 공개 모드에서만 데이터가 반환된다.
     */
    @GetMapping("/{gameId}/recent-plays")
    public GameRecentPlayQueryService.RecentPlaysResponse getRecentPlays(
            @PathVariable long gameId,
            @RequestParam(
                    defaultValue = "PROTECTED")
            String mode) {

        return gameRecentPlayQueryService.getRecentPlays(
                gameId,
                mode);
    }
}