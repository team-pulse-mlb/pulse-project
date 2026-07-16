import unittest

from app.services.play_translation_guard import check_play_translation


class PlayTranslationGuardTestCase(unittest.TestCase):
    def assert_safe_translation(
        self,
        source_text: str,
        translated_text: str,
    ):
        result = check_play_translation(
            source_text=source_text,
            translated_text=translated_text,
        )

        self.assertTrue(result.spoiler_safe)
        self.assertEqual(result.violations, [])

    def assert_translation_violations_include(
        self,
        source_text: str,
        translated_text: str | None,
        expected_violations: list[str],
    ):
        result = check_play_translation(
            source_text=source_text,
            translated_text=translated_text,
        )

        self.assertFalse(result.spoiler_safe)

        for expected_violation in expected_violations:
            self.assertIn(
                expected_violation,
                result.violations,
            )

    def test_safe_single_to_center_translation_passes(self):
        self.assert_safe_translation(
            source_text="Soto singled to center.",
            translated_text="Soto, 중견수 방면 안타",
        )

    def test_safe_looking_strikeout_translation_passes(self):
        self.assert_safe_translation(
            source_text="Marsh struck out looking.",
            translated_text="Marsh, 루킹 삼진",
        )

    def test_safe_swinging_strikeout_translation_passes(self):
        self.assert_safe_translation(
            source_text="Abrams struck out swinging.",
            translated_text="Abrams, 헛스윙 삼진",
        )

    def test_safe_home_run_translation_allows_home_run_word(self):
        self.assert_safe_translation(
            source_text="Soto homered to center.",
            translated_text="Soto, 중견수 방면 홈런",
        )

    def test_safe_walk_off_home_run_translation_allows_walk_off_word(self):
        self.assert_safe_translation(
            source_text="Soto hit a walk-off home run.",
            translated_text="Soto, 끝내기 홈런",
        )

    def test_safe_groundout_translation_passes(self):
        self.assert_safe_translation(
            source_text="Trout grounded out to third.",
            translated_text="Trout, 땅볼 아웃",
        )

    def test_safe_hit_by_pitch_translation_passes(self):
        self.assert_safe_translation(
            source_text="Soto hit by pitch.",
            translated_text="Soto, 몸에 맞는 공",
        )

    def test_safe_stolen_base_translation_passes(self):
        # YAML의 stole_second 규칙은 베이스와 도루를 모두 보존합니다.
        self.assert_safe_translation(
            source_text="Soto stole second.",
            translated_text="Soto, 2루 도루",
        )

    def test_safe_double_allows_semantic_number(self):
        # 원문에 숫자 2가 직접 없더라도 doubled는
        # 한국 야구 용어인 2루타로 번역할 수 있어야 합니다.
        self.assert_safe_translation(
            source_text="Soto doubled to center.",
            translated_text="Soto, 중견수 방면 2루타",
        )

    def test_safe_explicit_number_preserved(self):
        # 원문에 있는 투구 번호가 그대로 보존되면 통과합니다.
        self.assert_safe_translation(
            source_text="Soto singled to center on pitch 12.",
            translated_text="Soto, 12구째 중견수 방면 안타",
        )

    def test_safe_wild_pitch_translation_passes(self):
        # wild pitch의 필수 한국어 표현인 폭투가 보존되면 통과합니다.
        self.assert_safe_translation(
            source_text="Soto advanced on a wild pitch.",
            translated_text="Soto, 폭투로 진루",
        )

    def test_rejects_missing_yaml_event_term(self):
        # 원문은 wild pitch인데 포일로 바뀌면 이벤트 보존 실패입니다.
        self.assert_translation_violations_include(
            source_text="Soto advanced on a wild pitch.",
            translated_text="Soto, 포일로 진루",
            expected_violations=[
                "MISSING_EVENT:WILD_PITCH",
            ],
        )

    def test_rejects_added_commentary_from_glossary(self):
        # YAML 전역 금지어에 등록된 임의 평가 표현을 차단합니다.
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 결정적인 중견수 방면 안타",
            expected_violations=[
                "ADDED_COMMENTARY:결정적인",
            ],
        )

    def test_rejects_empty_source_text(self):
        self.assert_translation_violations_include(
            source_text="",
            translated_text="Soto, 안타",
            expected_violations=[
                "SOURCE_TEXT_EMPTY",
            ],
        )

    def test_rejects_empty_translated_text(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="",
            expected_violations=[
                "TRANSLATED_TEXT_EMPTY",
            ],
        )

    def test_rejects_blank_translated_text(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="   ",
            expected_violations=[
                "TRANSLATED_TEXT_EMPTY",
            ],
        )

    def test_rejects_multiple_sentences(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text=(
                "Soto, 중견수 방면 안타. "
                "다음 문장입니다."
            ),
            expected_violations=[
                "MULTIPLE_SENTENCES",
            ],
        )

    def test_rejects_missing_player_name(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="중견수 방면 안타",
            expected_violations=[
                "MISSING_PLAYER_NAME:Soto",
            ],
        )

    def test_rejects_added_player_name(self):
        # Judge는 원문에 존재하지 않으므로 추가 선수로 차단합니다.
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto와 Judge, 중견수 방면 안타",
            expected_violations=[
                "ADDED_PLAYER_NAME:Judge",
            ],
        )

    def test_rejects_changed_player_name(self):
        # Soto를 Judge로 바꾸면 원문 선수 누락과 새 선수 추가를
        # 모두 반환해야 합니다.
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Judge, 중견수 방면 안타",
            expected_violations=[
                "MISSING_PLAYER_NAME:Soto",
                "ADDED_PLAYER_NAME:Judge",
            ],
        )

    def test_rejects_missing_number(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center on pitch 12.",
            translated_text="Soto, 중견수 방면 안타",
            expected_violations=[
                "MISSING_NUMBER:12",
            ],
        )

    def test_rejects_added_number(self):
        # 원문에 없는 12구째 정보를 번역문이 임의로 추가하면 차단합니다.
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 12구째 중견수 방면 안타",
            expected_violations=[
                "ADDED_NUMBER:12",
            ],
        )

    def test_rejects_changed_number(self):
        # 원문의 12를 13으로 바꾸면 12 누락과 13 추가를
        # 각각 확인할 수 있어야 합니다.
        self.assert_translation_violations_include(
            source_text="Soto singled to center on pitch 12.",
            translated_text="Soto, 13구째 중견수 방면 안타",
            expected_violations=[
                "MISSING_NUMBER:12",
                "ADDED_NUMBER:13",
            ],
        )

    def test_rejects_missing_single_event(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 중견수 방면 출루",
            expected_violations=[
                "MISSING_EVENT:SINGLE",
            ],
        )

    def test_rejects_missing_double_event(self):
        self.assert_translation_violations_include(
            source_text="Soto doubled to center.",
            translated_text="Soto, 중견수 방면 안타",
            expected_violations=[
                "MISSING_EVENT:DOUBLE",
            ],
        )

    def test_rejects_missing_triple_event(self):
        self.assert_translation_violations_include(
            source_text="Soto tripled to center.",
            translated_text="Soto, 중견수 방면 2루타",
            expected_violations=[
                "MISSING_EVENT:TRIPLE",
            ],
        )

    def test_rejects_missing_swinging_strikeout_detail(self):
        self.assert_translation_violations_include(
            source_text="Abrams struck out swinging.",
            translated_text="Abrams, 삼진",
            expected_violations=[
                "MISSING_EVENT:STRIKEOUT_SWINGING",
            ],
        )

    def test_rejects_missing_looking_strikeout_detail(self):
        self.assert_translation_violations_include(
            source_text="Marsh struck out looking.",
            translated_text="Marsh, 삼진",
            expected_violations=[
                "MISSING_EVENT:STRIKEOUT_LOOKING",
            ],
        )

    def test_rejects_missing_center_direction(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 안타",
            expected_violations=[
                "MISSING_DIRECTION:CENTER",
            ],
        )

    def test_rejects_missing_left_direction(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to left.",
            translated_text="Soto, 안타",
            expected_violations=[
                "MISSING_DIRECTION:LEFT",
            ],
        )

    def test_rejects_missing_right_direction(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to right.",
            translated_text="Soto, 안타",
            expected_violations=[
                "MISSING_DIRECTION:RIGHT",
            ],
        )

    def test_rejects_added_home_run_to_single(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 중견수 방면 홈런",
            expected_violations=[
                "MISSING_EVENT:SINGLE",
                "ADDED_RESULT:HOME_RUN",
            ],
        )

    def test_rejects_added_walk_off_when_source_does_not_allow_it(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 중견수 방면 끝내기 안타",
            expected_violations=[
                "ADDED_RESULT:WALK_OFF",
            ],
        )

    def test_rejects_added_score_when_source_does_not_allow_it(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text="Soto, 중견수 방면 안타로 득점",
            expected_violations=[
                "ADDED_RESULT:SCORE",
            ],
        )

    def test_rejects_added_win_and_loss_terms(self):
        self.assert_translation_violations_include(
            source_text="Soto singled to center.",
            translated_text=(
                "Soto, 중견수 방면 안타로 "
                "승리와 패배가 갈렸습니다"
            ),
            expected_violations=[
                "ADDED_RESULT:WIN",
                "ADDED_RESULT:LOSS",
            ],
        )

    def test_allows_score_word_when_source_mentions_scored(self):
        self.assert_safe_translation(
            source_text="Soto scored.",
            translated_text="Soto, 득점",
        )


if __name__ == "__main__":
    unittest.main()