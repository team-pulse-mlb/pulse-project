package com.pulse.api.user;

import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.api.user.dto.EmailCheckResponse;
import com.pulse.api.user.dto.SignupRequest;
import com.pulse.api.user.dto.SignupResponse;
import com.pulse.api.user.exception.DuplicateEmailException;
import com.pulse.api.user.exception.EmailVerificationException;
import com.pulse.api.user.exception.FavoriteTeamLimitExceededException;
import com.pulse.api.user.exception.InvalidFavoriteTeamException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    /*
     * 회원 계정 저장/조회 Repository.
     *
     * users 테이블과 연결된다.
     */
    private final MemberRepository memberRepository;

    /*
     * user_settings 테이블 저장/조회 Repository.
     *
     * 회원가입 성공 시 회원의 기본 스포일러/알림 설정을 함께 저장한다.
     */
    private final UserSettingRepository userSettingRepository;

    /*
     * user_favorite_teams 테이블 저장/조회 Repository.
     *
     * 회원가입 Step 2에서 선택한 관심팀 목록을 저장할 때 사용한다.
     */
    private final UserFavoriteTeamRepository userFavoriteTeamRepository;

    /*
     * teams 테이블 조회 Repository.
     *
     * 프론트에서 넘어온 selectedTeamIds가 실제 존재하는 팀 ID인지 검증할 때 사용한다.
     */
    private final TeamRepository teamRepository;

    /*
     * 비밀번호 암호화 도구.
     *
     * 회원가입 요청의 원문 비밀번호를 BCrypt 해시로 변환한 뒤 DB에 저장한다.
     */
    private final PasswordEncoder passwordEncoder;

    /*
     * 이메일 인증번호/인증 완료 상태를 관리하는 서비스.
     *
     * 회원가입 전에 인증 완료 여부를 확인하고,
     * 회원가입 트랜잭션 커밋 후 인증 완료 기록을 삭제한다.
     */
    private final EmailVerificationService emailVerificationService;

    // 회원 가입
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        /*
         * 이메일 앞뒤 공백 제거 및 소문자 통일.
         *
         * 같은 이메일이라도 대소문자만 다르게 가입되는 문제를 막기 위해
         * 서버에서 한 번 더 정규화한다.
         */
        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        /*
         * 이메일 중복 검사.
         *
         * 프론트에서 중복확인을 했더라도,
         * 실제 회원가입 요청 시점에 다시 검사해야 한다.
         */
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        /*
         * Redis에서 실제 이메일 인증 완료 여부 검사.
         *
         * 인증번호 확인 API를 통과한 이메일만 회원가입할 수 있다.
         */
        if (!emailVerificationService.isVerified(email)) {
            throw new EmailVerificationException(
                    "이메일 인증을 완료해 주세요."
            );
        }

        /*
         * 관심팀 ID 목록 검증.
         *
         * - null이면 빈 리스트로 처리
         * - 중복 ID는 제거
         * - 최대 3팀까지만 허용
         * - 0 이하의 잘못된 ID는 차단
         *
         * 이 검증은 프론트에서도 하겠지만,
         * 서버에서도 반드시 한 번 더 막아야 한다.
         */
        List<Long> selectedTeamIds = normalizeAndValidateSelectedTeamIds(
                request.getSelectedTeamIds()
        );

        /*
         * 알림 설정 객체.
         *
         * 프론트가 notificationSettings를 보내지 않으면
         * SignupRequest의 기본값이 들어가지만,
         * 혹시 null로 들어오는 경우를 대비해 한 번 더 기본값 처리한다.
         */
        SignupRequest.NotificationSettingsRequest notificationSettings =
                request.getNotificationSettings();

        if (notificationSettings == null) {
            notificationSettings =
                    new SignupRequest.NotificationSettingsRequest();
        }

        /*
         * 비밀번호 암호화 후 Member 생성.
         *
         * request.getPassword()는 원문 비밀번호다.
         * DB에는 passwordHash만 저장한다.
         */
        Member member = Member.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        /*
         * PostgreSQL users 테이블에 회원 저장.
         *
         * Member의 PK는 IDENTITY 전략이므로,
         * save 이후 user_id가 생성된다.
         */
        Member savedMember = memberRepository.save(member);

        /*
         * 회원의 기본 설정 저장.
         *
         * 저장 테이블:
         * user_settings
         *
         * 매핑:
         * notificationSettings.gameStart  -> favorite_team_game_start_alert
         * notificationSettings.surge      -> important_moment_alert
         * notificationSettings.gameSwitch -> game_switch_alert
         *
         * notificationSettings.all은 프론트 UI용이므로 저장하지 않는다.
         */
        UserSetting userSetting = UserSetting.create(
                savedMember,
                notificationSettings.isGameStart(),
                notificationSettings.isSurge(),
                notificationSettings.isGameSwitch()
        );

        userSettingRepository.save(userSetting);

        /*
         * 관심팀 저장.
         *
         * selectedTeamIds가 비어 있으면 관심팀 선택을 건너뛴 것으로 보고 저장하지 않는다.
         */
        if (!selectedTeamIds.isEmpty()) {

            /*
             * 프론트에서 넘어온 팀 ID가 실제 teams 테이블에 존재하는지 조회한다.
             */
            List<Team> teams = teamRepository.findAllById(selectedTeamIds);

            /*
             * 요청한 팀 개수와 실제 조회된 팀 개수가 다르면,
             * 존재하지 않는 team_id가 포함된 것이다.
             */
            if (teams.size() != selectedTeamIds.size()) {
                throw new InvalidFavoriteTeamException(
                        "존재하지 않는 관심팀이 포함되어 있습니다."
                );
            }

            /*
             * 조회된 Team 엔티티들을 UserFavoriteTeam 엔티티로 변환한다.
             *
             * 저장 테이블:
             * user_favorite_teams
             */
            List<UserFavoriteTeam> favoriteTeams = teams.stream()
                    .map(team -> UserFavoriteTeam.create(savedMember, team))
                    .toList();

            userFavoriteTeamRepository.saveAll(favoriteTeams);
        }

        /*
         * PostgreSQL 트랜잭션이 실제로 커밋된 다음
         * Redis의 이메일 인증 완료 기록 삭제.
         *
         * 이렇게 해야 DB 저장 도중 오류가 발생했을 때
         * 이메일 인증 완료 기록이 먼저 삭제되는 문제를 막을 수 있다.
         */
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        emailVerificationService.removeVerifiedEmail(email);
                    }
                }
        );

        return new SignupResponse(
                "SUCCESS",
                "회원가입 요청을 정상적으로 받았습니다."
        );
    }

    /*
     * 회원가입 요청에 포함된 관심팀 ID 목록을 정리하고 검증한다.
     *
     * 이 메서드를 따로 분리한 이유:
     * signup() 메서드가 너무 길어지는 것을 막고,
     * 관심팀 정책 변경 시 이 부분만 수정하기 쉽게 하기 위해서다.
     */
    private List<Long> normalizeAndValidateSelectedTeamIds(
            List<Long> selectedTeamIds
    ) {
        /*
         * 관심팀 선택은 건너뛸 수 있으므로,
         * null이면 빈 리스트로 처리한다.
         */
        if (selectedTeamIds == null || selectedTeamIds.isEmpty()) {
            return List.of();
        }

        /*
         * LinkedHashSet을 사용하는 이유:
         * - 중복 팀 ID 제거
         * - 사용자가 선택한 순서 유지
         *
         * 예:
         * [101, 102, 101] -> [101, 102]
         */
        Set<Long> uniqueTeamIds = new LinkedHashSet<>(
                selectedTeamIds
        );

        /*
         * teamId는 PK이므로 null이거나 0 이하이면 잘못된 요청이다.
         */
        boolean hasInvalidTeamId = uniqueTeamIds.stream()
                .anyMatch(teamId -> teamId == null || teamId <= 0);

        if (hasInvalidTeamId) {
            throw new InvalidFavoriteTeamException(
                    "관심팀 ID가 올바르지 않습니다."
            );
        }

        /*
         * P1 정책:
         * 관심팀은 최대 3개까지만 선택 가능.
         */
        if (uniqueTeamIds.size() > 3) {
            throw new FavoriteTeamLimitExceededException(
                    "관심팀은 최대 3개까지 선택할 수 있습니다."
            );
        }

        return new ArrayList<>(uniqueTeamIds);
    }

    // 이메일 중복 체크
    public EmailCheckResponse checkEmail(String email) {

        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        boolean available =
                !memberRepository.existsByEmail(normalizedEmail);

        if (available) {
            return new EmailCheckResponse(
                    "SUCCESS",
                    true,
                    "사용 가능한 이메일입니다."
            );
        }

        return new EmailCheckResponse(
                "DUPLICATE_EMAIL",
                false,
                "이미 사용 중인 이메일입니다."
        );
    }
}