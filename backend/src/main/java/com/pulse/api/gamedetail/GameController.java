package com.pulse.api.gamedetail;

import com.pulse.api.gamedetail.GameEventQueryService.GameEventsResponse;
import com.pulse.api.gamedetail.GameQueryService.GameDetailView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "경기 상세", description = "스포일러 보호·공개 모드별 경기 상세와 흐름 조회")
public class GameController {

    private final GameQueryService gameQueryService;
    private final GameEventQueryService gameEventQueryService;
    private final GameRecentPlayQueryService gameRecentPlayQueryService;

    @Operation(
            summary = "경기 상세 조회",
            description = """
                    경기 상태와 표시 모드에 맞는 상세 정보를 조회한다.
                    보호 모드에서는 점수와 결과 방향을 드러내는 필드를 제외한다.
                    예정·진행·종료 상태와 표시 모드 조합에 따라 응답 스키마가 달라진다.
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
                            """,
                    schema = @Schema(
                            allowableValues = {"PROTECTED", "REVEALED"},
                            defaultValue = "PROTECTED"
                    )
            )
            @RequestParam(defaultValue = "protected")
            String mode,
            Authentication authentication
    ) {
        return gameQueryService.getGameDetail(
                gameId,
                mode,
                username(authentication)
        );
    }

    private static String username(
            Authentication authentication
    ) {
        return authentication == null
                || authentication
                instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
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
                    보호 모드에서는 타임라인 하이라이트로 선정된 스포일러 안전 이벤트만 반환한다.
                    공개 모드와 알 수 없는 모드는 빈 목록을 반환한다.
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
                            protected에서만 이벤트를 반환하고 revealed는 빈 목록을 반환한다.
                            """,
                    schema = @Schema(
                            allowableValues = {"PROTECTED", "REVEALED"},
                            defaultValue = "PROTECTED"
                    )
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
    @Operation(
            summary = "경기 최근 플레이 조회",
            description = """
                    공개 모드에서는 타석 결과 플레이를 최신순으로 반환한다.
                    보호 모드와 알 수 없는 모드는 빈 목록을 반환한다.
                    번역문이 없으면 원문과 translated=false를 반환한다.
                    """
    )
    @GetMapping("/{gameId}/recent-plays")
    public GameRecentPlayQueryService.RecentPlaysResponse getRecentPlays(
            @Parameter(description = "balldontlie 경기 ID", example = "5059041")
            @PathVariable long gameId,

            @Parameter(
                    description = "revealed에서만 플레이를 반환한다.",
                    schema = @Schema(
                            allowableValues = {"PROTECTED", "REVEALED"},
                            defaultValue = "PROTECTED"
                    )
            )
            @RequestParam(
                    defaultValue = "PROTECTED")
            String mode) {

        return gameRecentPlayQueryService.getRecentPlays(
                gameId,
                mode);
    }
}
