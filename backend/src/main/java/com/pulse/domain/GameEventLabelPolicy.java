package com.pulse.domain;

/**
 * game_events 이벤트의 모드별 표기 라벨 정책(SPOILER_POLICY.md §8).
 * 라벨 존재 여부가 아니라 spoiler_level을 먼저 검사하며,
 * 미지 event_type·미분류 spoiler_level은 두 모드 모두 null(기본 차단)이다.
 */
public final class GameEventLabelPolicy {

    private GameEventLabelPolicy() {
    }

    public static String protectedLabel(String spoilerLevel, String eventType) {
        if (!GameEvent.SPOILER_PROTECTED_SAFE.equals(spoilerLevel) || eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "pressure_bases_loaded" -> "만루 승부";
            case "pressure_scoring_position" -> "득점권 승부";
            case "long_at_bat" -> "긴 접전 승부";
            case "full_count_two_out" -> "승부처 카운트";
            case "pitcher_instability" -> "투수 흔들림";
            case "hard_contact" -> "강한 타구";
            default -> null;
        };
    }

    public static String revealedLabel(String spoilerLevel, String eventType) {
        if (GameEvent.SPOILER_PROTECTED_SAFE.equals(spoilerLevel)) {
            return protectedLabel(spoilerLevel, eventType);
        }
        if (!GameEvent.SPOILER_REVEALED_ONLY.equals(spoilerLevel) || eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "scoring_play" -> "득점";
            case "lead_change" -> "리드 교체";
            case "home_run" -> "홈런";
            case "big_inning" -> "빅이닝";
            default -> null;
        };
    }
}
