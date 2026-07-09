package com.pulse.api.user;

import com.pulse.api.user.dto.UserPreferenceResponse;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members/me/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /*
     * 로그인한 사용자의 관심팀/알림 설정을 조회한다.
     *
     * Authentication.getName():
     * - 현재 CustomUserDetailsService에서 username으로 email을 넣고 있으므로
     * - 여기서는 로그인한 사용자의 email이 반환된다.
     */
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