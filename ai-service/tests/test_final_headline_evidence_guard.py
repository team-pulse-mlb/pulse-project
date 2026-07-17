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
                    ],
                )
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
