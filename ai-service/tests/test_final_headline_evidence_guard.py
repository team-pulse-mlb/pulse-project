import unittest

from app.schemas.ai_schema import (
    AiCopyMode,
    PlayerInfo,
    SafeContext,
    SummaryFacts,
    VerifiedPlay,
)
from app.prompts.spoiler_free_prompt import (
    FINAL_HEADLINE_ALLOWED_FACT_IDS,
)
from app.services.final_headline_evidence_guard import (
    FINAL_HEADLINE_FACT_ATTRIBUTES,
    validate_final_headline_evidence,
)


class FinalHeadlineEvidenceGuardTestCase(unittest.TestCase):
    def setUp(self):
        self.safe_context = SafeContext(
            summary_facts=SummaryFacts(
                winner_side="home",
                winner_score=5,
                loser_score=3,
                decisive_inning=9,
                comeback_win=True,
                walk_off=True,
                total_runs=8,
            ),
            verified_plays=[
                VerifiedPlay(
                    play_id=312,
                    inning=9,
                    inning_type="bottom",
                    scoring_play=True,
                    batter=PlayerInfo(
                        id=660271,
                        name="Shohei Ohtani",
                    ),
                    pitcher=PlayerInfo(
                        id=592789,
                        name="Logan Webb",
                    ),
                    fact_tags=[
                        "DECISIVE_SCORE",
                        "COMEBACK_WIN",
                        "WALK_OFF",
                        "HIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=313,
                    scoring_play=True,
                    fact_tags=[
                        "TYING_SCORE",
                        "HIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=314,
                    scoring_play=True,
                    fact_tags=[
                        "INSURANCE_SCORE",
                        "HIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=315,
                    scoring_play=True,
                    fact_tags=[
                        "RUNS_SCORED",
                        "TRAILS_AFTER",
                        "CUTS_DEFICIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=316,
                    scoring_play=True,
                    fact_tags=[
                        "TAKES_LEAD",
                        "LEADS_AFTER",
                    ],
                ),
            ],
        )

    def test_prompt_and_guard_fact_id_contracts_match(self):
        self.assertEqual(
            set(FINAL_HEADLINE_ALLOWED_FACT_IDS),
            set(FINAL_HEADLINE_FACT_ATTRIBUTES),
        )

    def test_revealed_existing_fact_and_play_ids_pass(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[
                "summaryFacts.comebackWin",
                "summaryFacts.walkOff",
                "summaryFacts.decisiveInning",
            ],
            used_play_ids=[312],
        )

        self.assertTrue(result["evidence_safe"])
        self.assertEqual(result["violations"], [])

    def test_unknown_fact_id_is_blocked(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=["summaryFacts.unknownFact"],
            used_play_ids=[],
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_FACT_UNKNOWN:summaryFacts.unknownFact",
            result["violations"],
        )

    def test_unavailable_fact_id_is_blocked(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=["summaryFacts.shutout"],
            used_play_ids=[],
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_FACT_UNAVAILABLE:summaryFacts.shutout",
            result["violations"],
        )

    def test_unknown_play_id_is_blocked(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[999],
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_PLAY_UNKNOWN:999",
            result["violations"],
        )

    def test_duplicate_evidence_ids_are_blocked(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[
                "summaryFacts.walkOff",
                "summaryFacts.walkOff",
            ],
            used_play_ids=[312, 312],
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_FACT_DUPLICATE:summaryFacts.walkOff",
            result["violations"],
        )
        self.assertIn(
            "EVIDENCE_PLAY_DUPLICATE:312",
            result["violations"],
        )

    def test_comeback_claim_requires_declared_true_fact(self):
        missing_result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[],
            text="후반 역전 장면",
        )

        self.assertFalse(missing_result["evidence_safe"])
        self.assertIn(
            (
                "EVIDENCE_REQUIRED_FACT_MISSING:"
                "summaryFacts.comebackWin"
            ),
            missing_result["violations"],
        )

        false_context = SafeContext(
            summary_facts=SummaryFacts(
                comeback_win=False,
            )
        )

        false_result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=false_context,
            used_fact_ids=[
                "summaryFacts.comebackWin",
            ],
            used_play_ids=[],
            text="후반 역전 장면",
        )

        self.assertFalse(false_result["evidence_safe"])
        self.assertIn(
            (
                "EVIDENCE_REQUIRED_FACT_FALSE:"
                "summaryFacts.comebackWin"
            ),
            false_result["violations"],
        )

    def test_walk_off_and_shutout_claims_require_matching_facts(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[
                "summaryFacts.walkOff",
                "summaryFacts.shutout",
            ],
            used_play_ids=[],
            text="9회 끝내기와 영봉",
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            (
                "EVIDENCE_FACT_UNAVAILABLE:"
                "summaryFacts.shutout"
            ),
            result["violations"],
        )
        self.assertIn(
            (
                "EVIDENCE_REQUIRED_FACT_FALSE:"
                "summaryFacts.shutout"
            ),
            result["violations"],
        )

    def test_decisive_claim_requires_used_decisive_score_play(self):
        passing_result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[312],
            text="9회 결승타",
        )

        self.assertTrue(passing_result["evidence_safe"])
        self.assertEqual(passing_result["violations"], [])

        failing_result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[],
            text="9회 결정적 득점",
        )

        self.assertFalse(failing_result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:DECISIVE_SCORE",
            failing_result["violations"],
        )

    def test_decisive_hit_claim_requires_hit_tag(self):
        context = SafeContext(
            verified_plays=[
                VerifiedPlay(
                    play_id=900,
                    fact_tags=[
                        "DECISIVE_SCORE",
                    ],
                )
            ]
        )

        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=context,
            used_fact_ids=[],
            used_play_ids=[900],
            text="9회 결승타",
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:HIT",
            result["violations"],
        )

    def test_additional_flow_claims_pass_with_declared_matching_plays(self):
        cases = [
            ("7회 동점타", 313),
            ("8회 쐐기타", 314),
            ("한 차례 실점", 315),
            ("열세에서도 한 점 차로 따라붙었습니다", 315),
            ("리드를 잡고 앞섰습니다", 316),
            ("우세를 이어갔습니다", 316),
        ]

        for text, play_id in cases:
            with self.subTest(text=text):
                result = validate_final_headline_evidence(
                    mode=AiCopyMode.REVEALED,
                    safe_context=self.safe_context,
                    used_fact_ids=[],
                    used_play_ids=[play_id],
                    text=text,
                )

                self.assertTrue(result["evidence_safe"])
                self.assertEqual(result["violations"], [])

    def test_additional_flow_claims_reject_missing_used_play_evidence(self):
        cases = [
            (
                "7회 동점타",
                "EVIDENCE_REQUIRED_PLAY_TAGS_MISSING:"
                "TYING_SCORE+HIT",
            ),
            (
                "8회 쐐기",
                "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:"
                "INSURANCE_SCORE",
            ),
            (
                "한 차례 실점",
                "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:"
                "RUNS_SCORED",
            ),
            (
                "열세에서 따라붙었습니다",
                "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:"
                "TRAILS_AFTER",
            ),
            (
                "리드를 잡았습니다",
                "EVIDENCE_REQUIRED_PLAY_TAG_ANY_MISSING:"
                "LEAD_CHANGE|TAKES_LEAD|LEADS_AFTER",
            ),
        ]

        for text, expected_violation in cases:
            with self.subTest(text=text):
                result = validate_final_headline_evidence(
                    mode=AiCopyMode.REVEALED,
                    safe_context=self.safe_context,
                    used_fact_ids=[],
                    used_play_ids=[],
                    text=text,
                )

                self.assertFalse(result["evidence_safe"])
                self.assertIn(
                    expected_violation,
                    result["violations"],
                )

    def test_player_name_linked_to_used_play_passes(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[312],
            text="Ohtani의 9회 결승타",
        )

        self.assertTrue(result["evidence_safe"])
        self.assertEqual(result["violations"], [])

    def test_pitcher_name_linked_to_used_play_passes(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[312],
            text="Webb이 등판한 경기",
        )

        self.assertTrue(result["evidence_safe"])
        self.assertEqual(result["violations"], [])

    def test_player_name_without_used_play_is_blocked(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.REVEALED,
            safe_context=self.safe_context,
            used_fact_ids=[],
            used_play_ids=[],
            text="Ohtani가 활약한 경기",
        )

        self.assertFalse(result["evidence_safe"])
        self.assertIn(
            "EVIDENCE_PLAYER_PLAY_MISSING:Shohei Ohtani",
            result["violations"],
        )

    def test_protected_mode_rejects_result_evidence(self):
        result = validate_final_headline_evidence(
            mode=AiCopyMode.PROTECTED,
            safe_context=SafeContext(),
            used_fact_ids=["summaryFacts.walkOff"],
            used_play_ids=[312],
        )

        self.assertFalse(result["evidence_safe"])
        self.assertEqual(
            result["violations"],
            ["EVIDENCE_NOT_ALLOWED_IN_PROTECTED_MODE"],
        )


if __name__ == "__main__":
    unittest.main()
