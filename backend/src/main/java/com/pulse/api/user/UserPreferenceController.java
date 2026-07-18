package com.pulse.api.user;

import com.pulse.api.user.dto.UserPreferenceResponse;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import com.pulse.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/preferences")
@RequiredArgsConstructor
@Tag(name = "사용자 설정", description = "현재 사용자의 관심 팀·선수와 알림 설정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /*
     * 로그인한 사용자의 관심팀/알림 설정을 조회한다.
     *
     * Authentication.getName():
     * - 현재 CustomUserDetailsService에서 username으로 email을 넣고 있으므로
     * - 여기서는 로그인한 사용자의 email이 반환된다.
     */
    @Operation(summary = "내 설정 조회")
    @GetMapping
    public ResponseEntity<UserPreferenceResponse> getMyPreferences(
            Authentication authentication
    ) {
        UserPreferenceResponse response =
                userPreferenceService.getMyPreferences(
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }

    /*
     * 로그인한 사용자의 관심팀/알림 설정을 수정한다.
     */
    @Operation(
            summary = "내 설정 수정",
            description = """
                    관심 팀과 알림 설정을 변경한다.
                    selectedPlayerIds가 null이면 기존 관심 선수를 유지하고, 빈 배열이면 모두 해제한다.
                    """
    )
    @PutMapping
    public ResponseEntity<UserPreferenceResponse> updateMyPreferences(
            Authentication authentication,
            @Valid @RequestBody UserPreferenceUpdateRequest request
    ) {
        UserPreferenceResponse response =
                userPreferenceService.updateMyPreferences(
                        authentication.getName(),
                        request
                );

        return ResponseEntity.ok(response);
    }
}
