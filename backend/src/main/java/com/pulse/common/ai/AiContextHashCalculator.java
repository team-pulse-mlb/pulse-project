package com.pulse.common.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * AI 문구 생성 컨텍스트의 결정적 해시(SHA-256 소문자 hex)를 계산한다.
 * 봉투(schemaVersion·purpose·mode·gameId·eventId)와 safeContext를
 * 키 사전순·null 제외·공백 없는 JSON으로 정규화해 모드가 다르면 해시도 달라진다.
 * 창현 모듈은 이 해시를 재계산하지 않고 그대로 왕복시켜 stale write를 막는다.
 */
public final class AiContextHashCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private AiContextHashCalculator() {
    }

    public static String calculate(String purpose, AiCopyMode mode, long gameId, Long eventId,
                                   Map<String, Object> safeContext) {
        ObjectNode envelope = OBJECT_MAPPER.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("purpose", purpose);
        envelope.put("mode", mode.name());
        envelope.put("gameId", gameId);
        if (eventId != null) {
            envelope.put("eventId", eventId);
        }
        envelope.set("safeContext", withoutNulls(OBJECT_MAPPER.valueToTree(safeContext)));
        return hash(envelope);
    }

    /**
     * 공개 최근 플레이 번역의 최신 원문을 식별하는 해시를 계산한다.
     *
     * 이벤트 문구의 eventId와 혼동하지 않도록 playId를 별도 필드로 사용하고,
     * 번역 목적·공개 모드·대상 언어를 함께 포함한다.
     */
    public static String calculatePlayTranslation(
            long gameId,
            long playId,
            String sourceText
    ) {
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException(
                    "플레이 번역 원문은 비어 있을 수 없습니다.");
        }

        ObjectNode envelope = OBJECT_MAPPER.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("purpose", "PLAY_TRANSLATION");
        envelope.put("mode", AiCopyMode.REVEALED.name());
        envelope.put("gameId", gameId);
        envelope.put("playId", playId);
        envelope.put("sourceText", sourceText);
        envelope.put("targetLanguage", "ko");

        return hash(envelope);
    }

    private static String hash(
            ObjectNode envelope
    ) {
        try {
            byte[] serialized =
                    OBJECT_MAPPER
                            .writeValueAsString(
                                    withoutNulls(envelope))
                            .getBytes(
                                    StandardCharsets.UTF_8);

            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "AI 컨텍스트 해시 계산에 실패했습니다.",
                    exception);
        }
    }

    private static JsonNode withoutNulls(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            java.util.TreeMap<String, JsonNode> fields = new java.util.TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> {
                if (!value.isNull()) {
                    result.set(key, withoutNulls(value));
                }
            });
            return result;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode result = OBJECT_MAPPER.createArrayNode();
            node.forEach(value -> result.add(withoutNulls(value)));
            return result;
        }
        return node;
    }
}
