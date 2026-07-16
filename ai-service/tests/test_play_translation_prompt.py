import unittest

from app.prompts.play_translation_prompt import (
    build_play_translation_prompt,
    find_matching_directions,
    find_matching_event_terms,
    find_matching_modifiers,
    find_matching_positions,
    load_baseball_terms,
    normalize_source_text,
)
from app.schemas.ai_schema import AiCopyMode, PlayTranslationRequest


class PlayTranslationPromptTestCase(unittest.TestCase):
    def setUp(self):
        # 각 테스트가 YAML loader cache 상태에 영향을 받지 않도록 초기화합니다.
        load_baseball_terms.cache_clear()

    def tearDown(self):
        # 다른 테스트 모듈이 실행될 때도 cache 상태가 남지 않도록 정리합니다.
        load_baseball_terms.cache_clear()

    @staticmethod
    def _request(source_text: str) -> PlayTranslationRequest:
        # 반복되는 정상 PLAY_TRANSLATION 요청 데이터를 한곳에서 생성합니다.
        return PlayTranslationRequest(
            game_id=5059041,
            play_id=312,
            mode=AiCopyMode.REVEALED,
            context_hash="play-312-v1",
            source_text=source_text,
            target_language="ko",
        )

    def test_load_baseball_terms_returns_required_sections_and_uses_cache(self):
        first = load_baseball_terms()
        second = load_baseball_terms()
        cache_info = load_baseball_terms.cache_info()

        # 두 번째 호출은 같은 캐시 객체를 반환해야 합니다.
        self.assertIs(first, second)
        self.assertEqual(cache_info.misses, 1)
        self.assertEqual(cache_info.hits, 1)

        # prompt builder가 사용하는 필수 용어집 섹션을 검증합니다.
        self.assertEqual(
            first["metadata"]["purpose"],
            "PLAY_TRANSLATION",
        )
        self.assertIn("event_terms", first)
        self.assertIn("modifiers", first)
        self.assertIn("position_patterns", first)
        self.assertIn("direction_patterns", first)

    def test_normalize_source_text_normalizes_case_quotes_dashes_and_spaces(self):
        source_text = "  Fielder’s   Choice — To   Third  "

        normalized = normalize_source_text(source_text)

        self.assertEqual(
            normalized,
            "fielder's choice - to third",
        )

    def test_specific_swinging_strikeout_overrides_generic_strikeout(self):
        matched = find_matching_event_terms(
            "Abrams struck out swinging."
        )

        self.assertEqual(
            [item["id"] for item in matched],
            ["strikeout_swinging"],
        )
        self.assertEqual(
            matched[0]["canonicalKo"],
            "헛스윙 삼진",
        )

        # 내부 정렬용 필드는 최종 prompt 규칙에 노출하지 않아야 합니다.
        self.assertNotIn("priority", matched[0])
        self.assertNotIn("span", matched[0])

    def test_specific_looking_strikeout_is_matched(self):
        matched = find_matching_event_terms(
            "Freeman struck out looking."
        )

        self.assertEqual(
            [item["id"] for item in matched],
            ["strikeout_looking"],
        )
        self.assertEqual(
            matched[0]["canonicalKo"],
            "루킹 삼진",
        )

    def test_specific_infield_single_overrides_generic_single(self):
        matched = find_matching_event_terms(
            "Soto singled on a ground ball."
        )

        self.assertEqual(
            [item["id"] for item in matched],
            ["infield_single"],
        )
        self.assertEqual(
            matched[0]["canonicalKo"],
            "내야 안타",
        )

    def test_single_to_center_matches_event_and_direction_only(self):
        source_text = "Soto singled to center."

        events = find_matching_event_terms(source_text)
        directions = find_matching_directions(source_text)
        positions = find_matching_positions(source_text)

        self.assertEqual(
            [item["id"] for item in events],
            ["single"],
        )
        self.assertEqual(
            [item["id"] for item in directions],
            ["center"],
        )

        # 안타의 to center는 중견수 포지션이 아니라 타구 방향입니다.
        self.assertEqual(positions, [])

    def test_groundout_to_third_matches_position_not_direction(self):
        source_text = "Trout grounded out to third."

        events = find_matching_event_terms(source_text)
        directions = find_matching_directions(source_text)
        positions = find_matching_positions(source_text)

        self.assertEqual(
            [item["id"] for item in events],
            ["groundout"],
        )

        # 땅볼 아웃의 to third를 단순 3루 방향으로 중복 해석하면 안 됩니다.
        self.assertEqual(directions, [])
        self.assertEqual(
            [item["id"] for item in positions],
            ["third_baseman"],
        )

    def test_lineout_to_second_matches_position_not_direction(self):
        source_text = "Trout lined out to second."

        events = find_matching_event_terms(source_text)
        directions = find_matching_directions(source_text)
        positions = find_matching_positions(source_text)

        self.assertEqual(
            [item["id"] for item in events],
            ["lineout"],
        )
        self.assertEqual(directions, [])
        self.assertEqual(
            [item["id"] for item in positions],
            ["second_baseman"],
        )

    def test_base_number_is_not_misclassified_as_position(self):
        positions = find_matching_positions(
            "Runner advanced to second."
        )

        # 주자가 2루로 진루한 문장의 second는 2루수가 아닙니다.
        self.assertEqual(positions, [])

    def test_walk_off_home_run_matches_event_modifier_and_direction(self):
        source_text = "Judge hit a walk-off home run to left."

        events = find_matching_event_terms(source_text)
        modifiers = find_matching_modifiers(source_text)
        directions = find_matching_directions(source_text)

        self.assertEqual(
            [item["id"] for item in events],
            ["home_run"],
        )
        self.assertEqual(
            [item["id"] for item in modifiers],
            ["walk_off"],
        )
        self.assertEqual(
            [item["id"] for item in directions],
            ["left"],
        )

    def test_prompt_contains_only_rules_relevant_to_source_text(self):
        prompt = build_play_translation_prompt(
            self._request("Soto singled to center.")
        )

        self.assertIn(
            '"purpose": "PLAY_TRANSLATION"',
            prompt,
        )
        self.assertIn(
            '"mode": "REVEALED"',
            prompt,
        )
        self.assertIn(
            '"sourceText": "Soto singled to center."',
            prompt,
        )
        self.assertIn('"id": "single"', prompt)
        self.assertIn('"id": "center"', prompt)

        # 입력 문장과 관련 없는 전체 용어를 prompt에 넣으면 안 됩니다.
        self.assertNotIn('"id": "home_run"', prompt)
        self.assertNotIn('"id": "balk"', prompt)
        self.assertNotIn(
            '"id": "strikeout_swinging"',
            prompt,
        )

    def test_prompt_contains_global_translation_and_output_rules(self):
        prompt = build_play_translation_prompt(
            self._request("Abrams struck out swinging.")
        )

        self.assertIn(
            "선수명은 철자와 표기를 바꾸지 말고",
            prompt,
        )
        self.assertIn(
            "숫자, 거리, 베이스 번호",
            prompt,
        )
        self.assertIn(
            "원문에 없는 점수",
            prompt,
        )
        self.assertIn(
            "JSON 객체 하나만 반환",
            prompt,
        )
        self.assertIn('"translated_text"', prompt)

        # YAML output_policy와 금지 표현도 prompt에 전달되어야 합니다.
        self.assertIn(
            '"preserve_player_names": true',
            prompt,
        )
        self.assertIn(
            '"maximum_sentences": 1',
            prompt,
        )
        self.assertIn("결정적인", prompt)

    def test_prompt_contains_canonical_term_and_position_preservation_rules(self):
        prompt = build_play_translation_prompt(
            self._request("Trout lined out to second.")
        )

        self.assertIn(
            "matchedRules.eventTerms[].canonicalKo",
            prompt,
        )
        self.assertIn(
            "matchedRules.eventTerms[].requiredKo",
            prompt,
        )
        self.assertIn(
            "matchedRules.positions가 비어 있지 않으면",
            prompt,
        )
        self.assertIn(
            '"lined out to second"는 "2루수 직선타 아웃"',
            prompt,
        )
        self.assertIn(
            '"라인드라이브 아웃"으로 바꾸지 마세요',
            prompt,
        )

    def test_lineout_to_second_prompt_requires_position_and_canonical_event_term(self):
        prompt = build_play_translation_prompt(
            self._request("Trout lined out to second.")
        )

        self.assertIn('"id": "lineout"', prompt)
        self.assertIn(
            '"canonicalKo": "직선타 아웃"',
            prompt,
        )
        self.assertIn('"requiredKo"', prompt)
        self.assertIn('"직선타"', prompt)
        self.assertIn('"아웃"', prompt)
        self.assertIn('"id": "second_baseman"', prompt)
        self.assertIn('"canonicalKo": "2루수"', prompt)

    def test_same_request_produces_same_prompt(self):
        request = self._request(
            "Betts doubled to right-center."
        )

        first = build_play_translation_prompt(request)
        second = build_play_translation_prompt(request)

        # 같은 입력과 같은 용어집이면 동일한 prompt가 생성되어야 합니다.
        self.assertEqual(first, second)

    def test_unmapped_source_text_builds_prompt_with_empty_matched_rules(self):
        prompt = build_play_translation_prompt(
            self._request("Pitching change.")
        )

        self.assertIn('"eventTerms": []', prompt)
        self.assertIn('"modifiers": []', prompt)
        self.assertIn('"directions": []', prompt)
        self.assertIn('"positions": []', prompt)

        # 미등록 원문에서도 모델이 결과를 추측하지 않도록 규칙은 유지합니다.
        self.assertIn(
            "matchedRules에 없는 야구 결과를 추측",
            prompt,
        )

    def test_blank_source_text_is_rejected_by_prompt_builder(self):
        request = self._request("   ")

        with self.assertRaisesRegex(
            ValueError,
            "sourceText must not be blank",
        ):
            build_play_translation_prompt(request)


if __name__ == "__main__":
    unittest.main()