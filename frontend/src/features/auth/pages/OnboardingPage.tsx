import SettingsPlayersPage from './SettingsPlayersPage';

/*
 * 회원가입 완료 후 로그인한 신규 사용자에게 보여주는
 * 관심 선수 선택 온보딩 화면입니다.
 *
 * 선수 검색과 저장 기능을 별도로 복사하지 않고,
 * 기존 관심 선수 설정 화면을 onboarding 모드로 재사용합니다.
 */
function OnboardingPage() {
    return (
        <SettingsPlayersPage mode="onboarding" />
    );
}

export default OnboardingPage;