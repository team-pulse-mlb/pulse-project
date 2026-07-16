import unittest

from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    FinalHeadlineRequest,
    FinalScore,
    KeyMoment,
    SafeContext,
)


class SpoilerFreePromptTestCase(unittest.TestCase):
    def test_final_headline_prompt_contains_protected_safe_context_values(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                tension_level="HIGH",
                score_band="RECOMMEND",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
                key_moments=[
                    KeyMoment(
                        inning=7,
                        label="만루 승부",
                    )
                ],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertIn('"language": "ko"', prompt)
        self.assertIn('"maxLength": 80', prompt)
        self.assertIn("STATUS_FINAL", prompt)
        self.assertIn("경기 종료", prompt)
        self.assertIn("HIGH", prompt)
        self.assertIn("RECOMMEND", prompt)
        self.assertIn("후반 긴장 구간", prompt)
        self.assertIn("late_or_extra", prompt)
        self.assertIn("만루 승부", prompt)

    def test_final_headline_protected_prompt_excludes_result_context(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                final_score=FinalScore(
                    home=5,
                    away=3,
                ),
                winner="home",
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertNotIn('"finalScore"', prompt)
        self.assertNotIn('"winner"', prompt)

    def test_final_headline_prompt_contains_protected_guard_rules(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("점수", prompt)
        self.assertIn("승패", prompt)
        self.assertIn("리드 팀", prompt)
        self.assertIn("홈런", prompt)
        self.assertIn("역전", prompt)
        self.assertIn("JSON 객체 하나만 반환", prompt)
        self.assertIn("safe_title", prompt)

    def test_final_headline_revealed_prompt_includes_result_context(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.REVEALED,
            context_hash="game-5059082-final-revealed-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                final_score=FinalScore(
                    home=5,
                    away=3,
                ),
                winner="home",
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"finalScore"', prompt)
        self.assertIn('"home": 5', prompt)
        self.assertIn('"away": 3', prompt)
        self.assertIn('"winner": "home"', prompt)

    def test_final_headline_revealed_prompt_contains_v2_context_values(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.REVEALED,
            context_hash="game-5059082-final-revealed-v2",
            safe_context=SafeContext(
                status="STATUS_FINAL",
                period_label="경기 종료",
                teams={
                    "home": {
                        "name": "Los Angeles Dodgers",
                        "abbr": "LAD",
                    },
                    "away": {
                        "name": "San Francisco Giants",
                        "abbr": "SF",
                    },
                },
                final_score=FinalScore(
                    home=5,
                    away=3,
                ),
                winner="home",
                innings_played=10,
                extra_innings=True,
                postseason=False,
                venue="Dodger Stadium",
                start_time="2026-07-03T00:05:00Z",
                home_inning_scores=[0, 0, 1, 0, 0, 0, 0, 2, 0, 2],
                away_inning_scores=[0, 0, 0, 1, 0, 0, 2, 0, 0, 0],
                summary_facts={
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
                revealed_events=[
                    {
                        "eventId": 91,
                        "eventType": "home_run",
                        "inning": 8,
                        "inningType": "Bottom",
                        "batter": {
                            "id": 660271,
                            "name": "Shohei Ohtani",
                        },
                        "pitcher": {
                            "id": 592789,
                            "name": "Logan Webb",
                        },
                        "evidence": {
                            "scoreValue": 2,
                        },
                    }
                ],
                revealed_moments=[
                    {
                        "inning": 8,
                        "inningHalf": "Bottom",
                        "battingTeam": "LAD",
                        "eventTypes": ["home_run"],
                        "batter": "Shohei Ohtani",
                        "runsScored": 2,
                        "scoreAfter": {
                            "home": 5,
                            "away": 3,
                        },
                        "scoringPlays": 1,
                    }
                ],
                verified_plays=[
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
                        "batter": {
                            "id": 660271,
                            "name": "Shohei Ohtani",
                        },
                        "pitcher": {
                            "id": 592789,
                            "name": "Logan Webb",
                        },
                        "runnerOnFirst": False,
                        "runnerOnSecond": True,
                        "runnerOnThird": False,
                        "factTags": ["SCORING_PLAY", "TRANSLATED"],
                    }
                ],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"status": "STATUS_FINAL"', prompt)
        self.assertIn('"periodLabel": "경기 종료"', prompt)
        self.assertIn('"teams"', prompt)
        self.assertIn('"name": "Los Angeles Dodgers"', prompt)
        self.assertIn('"abbr": "LAD"', prompt)
        self.assertIn('"venue": "Dodger Stadium"', prompt)
        self.assertIn('"summaryFacts"', prompt)
        self.assertIn('"winnerName": "Los Angeles Dodgers"', prompt)
        self.assertIn('"scoreGap": 2', prompt)
        self.assertIn('"revealedEvents"', prompt)
        self.assertIn('"eventType": "home_run"', prompt)
        self.assertIn('"scoreValue": 2', prompt)
        self.assertIn('"revealedMoments"', prompt)
        self.assertIn('"verifiedPlays"', prompt)
        self.assertIn('"sourceText": "Ohtani homered to right center."', prompt)
        self.assertIn('"translatedText": "Ohtani, 우중간 홈런"', prompt)
        self.assertIn('"factTags": [', prompt)
        self.assertIn('"TRANSLATED"', prompt)
        self.assertIn("summaryFacts", prompt)
        self.assertIn("verifiedPlays", prompt)
        self.assertIn("translatedText가 있으면 sourceText보다 translatedText를 우선", prompt)
        self.assertIn(
            '승자 주어는 반드시 "홈팀", "원정팀", teams.home.name',
            prompt,
        )
        self.assertIn(
            "영문 팀명을 한국어로 번역·음역·축약하거나 별칭으로 바꾸지 마세요",
            prompt,
        )
        self.assertIn(
            'teams.home.name이 "Los Angeles Dodgers"여도 "다저스"를 새로 만들어 사용하지 마세요',
            prompt,
        )
        self.assertIn(
            '팀 주어 없이 "5-3 승리", "홈런으로 승리"처럼 작성하지 마세요',
            prompt,
        )


    def test_final_headline_revealed_prompt_contains_score_format_rules(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.REVEALED,
            context_hash="game-5059082-final-revealed-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                final_score=FinalScore(
                    home=5,
                    away=3,
                ),
                winner="home",
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("finalScore.home-finalScore.away", prompt)
        self.assertIn("홈팀 점수-원정팀 점수", prompt)
        self.assertIn('"5점"', prompt)
        self.assertIn('"5:3"', prompt)
        self.assertIn('"5대3"', prompt)
        self.assertIn("홈팀이 5-3으로 승리", prompt)
        self.assertIn("원정팀이 3-5로 승리", prompt)
        self.assertIn("승자 한 팀만", prompt)

    def test_final_headline_protected_prompt_excludes_event_copy_specific_rules(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertNotIn("EVENT_COPY PROTECTED 예시", prompt)
        self.assertNotIn("safe_title에는 이닝 숫자", prompt)
        self.assertNotIn('"~합니다"체로 작성하세요.', prompt)

    def test_event_copy_protected_prompt_contains_only_protected_event_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.PROTECTED,
            context_hash="event-91-protected-v1",
            safe_context=SafeContext(
                event_type="pressure_bases_loaded",
                label="만루 승부",
                inning=7,
                inning_type="Top",
                batter="Kim",
                pitcher="Steele",
                evidence={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertIn('"eventType": "pressure_bases_loaded"', prompt)
        self.assertIn('"label": "만루 승부"', prompt)
        self.assertIn('"inning": 7', prompt)
        self.assertNotIn('"inningType"', prompt)
        self.assertNotIn('"batter"', prompt)
        self.assertNotIn('"pitcher"', prompt)
        self.assertNotIn('"evidence"', prompt)

    def test_event_copy_protected_prompt_contains_situation_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.PROTECTED,
            context_hash="event-91-protected-v1",
            safe_context=SafeContext(
                event_type="full_count_two_out",
                label="승부처 카운트",
                inning=7,
                contributing_labels=[
                    "만루 승부",
                    "승부처 카운트",
                ],
                situation={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                    "runnerOnFirst": True,
                    "runnerOnSecond": True,
                    "runnerOnThird": True,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertIn('"contributingLabels": [', prompt)
        self.assertIn('"만루 승부"', prompt)
        self.assertIn('"승부처 카운트"', prompt)
        self.assertIn('"situation": {', prompt)
        self.assertIn('"outs": 2', prompt)
        self.assertIn('"balls": 3', prompt)
        self.assertIn('"strikes": 2', prompt)
        self.assertIn('"runnerOnFirst": true', prompt)
        self.assertIn('"runnerOnSecond": true', prompt)
        self.assertIn('"runnerOnThird": true', prompt)

    def test_event_copy_protected_prompt_contains_style_rules_and_examples(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.PROTECTED,
            context_hash="event-91-protected-v1",
            safe_context=SafeContext(
                event_type="full_count_two_out",
                label="승부처 카운트",
                inning=7,
                contributing_labels=[
                    "만루 승부",
                    "승부처 카운트",
                ],
                situation={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                    "runnerOnFirst": True,
                    "runnerOnSecond": True,
                    "runnerOnThird": True,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("EVENT_COPY PROTECTED 추가 규칙", prompt)
        self.assertIn(
            'safe_title에는 이닝 숫자, "회", "초", "말"을 쓰지 마세요.',
            prompt,
        )
        self.assertIn('"~합니다"체로 작성하세요.', prompt)
        self.assertIn("반드시 한 문장으로 작성하세요.", prompt)
        self.assertIn("상투 표현을 반복하지 마세요.", prompt)
        self.assertIn("EVENT_COPY PROTECTED 예시", prompt)
        self.assertIn(
            "2사 만루에서 풀카운트 승부가 이어졌습니다.",
            prompt,
        )
        self.assertIn(
            "1사 2루 상황에서 집중이 필요한 승부가 이어졌습니다.",
            prompt,
        )
        self.assertIn(
            "긴 타석 승부가 계속 이어졌습니다.",
            prompt,
        )

    def test_event_copy_revealed_prompt_contains_revealed_event_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.REVEALED,
            context_hash="event-91-revealed-v1",
            safe_context=SafeContext(
                event_type="pressure_bases_loaded",
                label="만루 승부",
                inning=7,
                inning_type="Top",
                batter="Kim",
                pitcher="Steele",
                evidence={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"eventType": "pressure_bases_loaded"', prompt)
        self.assertIn('"label": "만루 승부"', prompt)
        self.assertIn('"inning": 7', prompt)
        self.assertIn('"inningType": "Top"', prompt)
        self.assertIn('"batter": "Kim"', prompt)
        self.assertIn('"pitcher": "Steele"', prompt)
        self.assertIn('"evidence"', prompt)
        self.assertIn('"outs": 2', prompt)
        self.assertIn('"balls": 3', prompt)
        self.assertIn('"strikes": 2', prompt)

    def test_event_copy_revealed_prompt_excludes_protected_situation_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.REVEALED,
            context_hash="event-91-revealed-v1",
            safe_context=SafeContext(
                event_type="pressure_bases_loaded",
                label="만루 승부",
                inning=7,
                contributing_labels=[
                    "만루 승부",
                    "승부처 카운트",
                ],
                situation={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
                inning_type="Top",
                batter="Kim",
                pitcher="Steele",
                evidence={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"evidence"', prompt)
        self.assertNotIn('"contributingLabels"', prompt)
        self.assertNotIn('"situation"', prompt)
        self.assertNotIn("EVENT_COPY PROTECTED 예시", prompt)


if __name__ == "__main__":
    unittest.main()