import unittest

from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    FinalHeadlineRequest,
)


class AiSchemaTestCase(unittest.TestCase):
    def test_final_headline_request_accepts_spring_camel_case_payload(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
                "keyMoments": [
                    {
                        "inning": 7,
                        "label": "만루 승부",
                    }
                ],
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.mode, AiCopyMode.PROTECTED)
        self.assertEqual(
            request.context_hash,
            "game-5059082-final-protected-v1",
        )
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertEqual(request.safe_context.inning_phase, "경기 종료")
        self.assertEqual(request.safe_context.safe_tags, ["후반 긴장 구간"])
        self.assertEqual(request.safe_context.reason_codes, ["late_or_extra"])
        self.assertEqual(len(request.safe_context.key_moments), 1)
        self.assertEqual(request.safe_context.key_moments[0].inning, 7)
        self.assertEqual(request.safe_context.key_moments[0].label, "만루 승부")

    def test_final_headline_request_accepts_revealed_result_context(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": "game-5059082-final-revealed-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "finalScore": {
                    "home": 5,
                    "away": 3,
                },
                "winner": "home",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.mode, AiCopyMode.REVEALED)
        self.assertIsNotNone(request.safe_context.final_score)
        self.assertEqual(request.safe_context.final_score.home, 5)
        self.assertEqual(request.safe_context.final_score.away, 3)
        self.assertEqual(request.safe_context.winner, "home")

    def test_final_headline_request_accepts_v2_revealed_context(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": "game-5059082-final-revealed-v2",
            "safeContext": {
                "status": "STATUS_FINAL",
                "periodLabel": "경기 종료",
                "teams": {
                    "home": {"name": "Los Angeles Dodgers", "abbr": "LAD"},
                    "away": {"name": "San Francisco Giants", "abbr": "SF"},
                },
                "finalScore": {"home": 5, "away": 3},
                "winner": "home",
                "inningsPlayed": 10,
                "extraInnings": True,
                "postseason": False,
                "venue": "Dodger Stadium",
                "startTime": "2026-07-03T00:05:00Z",
                "homeInningScores": [0, 0, 1, 0, 0, 0, 0, 2, 0, 2],
                "awayInningScores": [0, 0, 0, 1, 0, 0, 2, 0, 0, 0],
                "summaryFacts": {
                    "winnerSide": "home",
                    "winnerName": "Los Angeles Dodgers",
                    "loserName": "San Francisco Giants",
                    "winnerScore": 5,
                    "loserScore": 3,
                    "extraInnings": True,
                    "finalInning": 10,
                    "scoreGap": 2,
                    "totalRuns": 8,
                },
                "revealedEvents": [
                    {
                        "eventId": 91,
                        "eventType": "home_run",
                        "inning": 8,
                        "inningType": "Bottom",
                        "batter": {"id": 660271, "name": "Shohei Ohtani"},
                        "pitcher": {"id": 592789, "name": "Logan Webb"},
                        "evidence": {"scoreValue": 2},
                    }
                ],
                "revealedMoments": [
                    {
                        "inning": 8,
                        "inningHalf": "Bottom",
                        "battingTeam": "LAD",
                        "eventTypes": ["home_run"],
                        "batter": "Shohei Ohtani",
                        "runsScored": 2,
                        "scoreAfter": {"home": 5, "away": 3},
                        "scoringPlays": 1,
                    }
                ],
                "verifiedPlays": [
                    {
                        "playId": 312,
                        "playOrder": 4250312,
                        "inning": 8,
                        "inningType": "bottom",
                        "sourceText": "Ohtani homered to right center.",
                        "translatedText": "Ohtani, 우중간 홈런",
                        "homeScoreAfter": 5,
                        "awayScoreAfter": 3,
                        "scoringPlay": True,
                        "scoreValue": 2,
                        "outs": 1,
                        "balls": 2,
                        "strikes": 1,
                        "batter": {"id": 660271, "name": "Shohei Ohtani"},
                        "pitcher": {"id": 592789, "name": "Logan Webb"},
                        "runnerOnFirst": False,
                        "runnerOnSecond": True,
                        "runnerOnThird": False,
                        "factTags": ["SCORING_PLAY", "TRANSLATED"],
                    }
                ],
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)
        safe_context = request.safe_context

        self.assertEqual(request.mode, AiCopyMode.REVEALED)
        self.assertEqual(safe_context.status, "STATUS_FINAL")
        self.assertEqual(safe_context.period_label, "경기 종료")
        self.assertIsNotNone(safe_context.teams)
        self.assertEqual(safe_context.teams.home.abbr, "LAD")
        self.assertEqual(safe_context.teams.away.abbr, "SF")
        self.assertEqual(safe_context.innings_played, 10)
        self.assertTrue(safe_context.extra_innings)
        self.assertEqual(safe_context.venue, "Dodger Stadium")
        self.assertEqual(safe_context.home_inning_scores[-1], 2)
        self.assertEqual(safe_context.away_inning_scores[3], 1)

        self.assertIsNotNone(safe_context.summary_facts)
        self.assertEqual(safe_context.summary_facts.winner_side, "home")
        self.assertEqual(safe_context.summary_facts.winner_name, "Los Angeles Dodgers")
        self.assertEqual(safe_context.summary_facts.score_gap, 2)
        self.assertEqual(safe_context.summary_facts.total_runs, 8)

        self.assertEqual(len(safe_context.revealed_events), 1)
        self.assertEqual(safe_context.revealed_events[0].event_id, 91)
        self.assertEqual(safe_context.revealed_events[0].batter.name, "Shohei Ohtani")
        self.assertEqual(safe_context.revealed_events[0].evidence["scoreValue"], 2)

        self.assertEqual(len(safe_context.revealed_moments), 1)
        self.assertEqual(safe_context.revealed_moments[0].inning_half, "Bottom")
        self.assertEqual(safe_context.revealed_moments[0].score_after.home, 5)

        self.assertEqual(len(safe_context.verified_plays), 1)
        self.assertEqual(safe_context.verified_plays[0].play_id, 312)
        self.assertEqual(safe_context.verified_plays[0].translated_text, "Ohtani, 우중간 홈런")
        self.assertEqual(safe_context.verified_plays[0].batter.name, "Shohei Ohtani")
        self.assertIn("TRANSLATED", safe_context.verified_plays[0].fact_tags)


    def test_event_copy_request_accepts_spring_camel_case_payload(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "REVEALED",
            "contextHash": "event-91-revealed-v1",
            "safeContext": {
                "eventType": "pressure_bases_loaded",
                "label": "만루 승부",
                "inning": 7,
                "inningType": "Top",
                "batter": "Kim",
                "pitcher": "Steele",
                "evidence": {
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            },
        }

        request = EventCopyRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059041)
        self.assertEqual(request.event_id, 91)
        self.assertEqual(request.mode, AiCopyMode.REVEALED)
        self.assertEqual(request.context_hash, "event-91-revealed-v1")
        self.assertEqual(
            request.safe_context.event_type,
            "pressure_bases_loaded",
        )
        self.assertEqual(request.safe_context.label, "만루 승부")
        self.assertEqual(request.safe_context.inning, 7)
        self.assertEqual(request.safe_context.inning_type, "Top")
        self.assertEqual(request.safe_context.batter, "Kim")
        self.assertEqual(request.safe_context.pitcher, "Steele")
        self.assertEqual(
            request.safe_context.evidence,
            {
                "outs": 2,
                "balls": 3,
                "strikes": 2,
            },
        )

    def test_extra_spring_fields_are_ignored(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "purpose": "CARD_SUMMARY",
            "status": "STATUS_FINAL",
            "startTime": "2026-07-03T00:05:00Z",
            "teams": [
                {"id": 28, "name": "Texas Rangers", "abbr": "TEX"},
                {"id": 10, "name": "Detroit Tigers", "abbr": "DET"},
            ],
            "recentPlays": [
                {
                    "type": "Play Result",
                    "text": "Osuna grounded out to second.",
                }
            ],
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
                "unexpectedField": "ignore me",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertFalse(hasattr(request, "recent_plays"))
        self.assertFalse(hasattr(request.safe_context, "unexpected_field"))

    def test_missing_optional_safe_context_lists_default_to_empty_lists(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.safe_context.safe_tags, [])
        self.assertEqual(request.safe_context.reason_codes, [])
        self.assertEqual(request.safe_context.key_moments, [])


if __name__ == "__main__":
    unittest.main()