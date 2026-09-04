package com.sago.domain.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 7 — 사고 사진에서 파손 부위·손상 유형·주요 객체를 인식해 태깅 (기획안 9.2 Prompt 4).
 * 인식 실패·오류 시 빈 결과를 반환한다 — 태깅이 안 돼도 다음 단계 진행을 막지 않고,
 * 사용자가 직접 태그를 수정할 수 있도록 한다(기획안 10절 예외처리).
 */
@Service
public class PhotoTaggingService {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.7");
    private static final int LABEL_MAX_LENGTH = 100;

    private final GeminiClient geminiClient;
    private final PhotoTagRepository photoTagRepository;
    private final ObjectMapper objectMapper;

    public PhotoTaggingService(GeminiClient geminiClient,
                                PhotoTagRepository photoTagRepository,
                                ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.photoTagRepository = photoTagRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PhotoTaggingResult tagPhoto(Photo photo, byte[] imageBytes, String mimeType) {
        String responseText;
        try {
            responseText = geminiClient.generateContentWithImage(buildPrompt(), imageBytes, mimeType);
        } catch (GeminiApiException e) {
            return PhotoTaggingResult.empty();
        }

        JsonNode result;
        try {
            String json = responseText
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
            result = objectMapper.readTree(json);
        } catch (Exception e) {
            return PhotoTaggingResult.empty();
        }

        if (!result.has("tags") || !result.get("tags").isArray()) {
            return PhotoTaggingResult.empty();
        }

        List<PhotoTag> tags = new ArrayList<>();
        for (JsonNode tagNode : result.get("tags")) {
            PhotoTag tag = toPhotoTag(photo, tagNode);
            if (tag != null) {
                tags.add(tag);
            }
        }

        // 같은 사진을 재태깅해도 중복 저장되지 않도록, 기존 태그를 지우고 새로 저장한다
        photoTagRepository.deleteByPhoto_PhotoId(photo.getPhotoId());
        List<PhotoTag> savedTags = photoTagRepository.saveAll(tags);
        List<String> additionalConfirmationItems = extractTextArray(result, "additionalConfirmationItems");

        return new PhotoTaggingResult(savedTags, additionalConfirmationItems);
    }

    private PhotoTag toPhotoTag(Photo photo, JsonNode tagNode) {
        if (!tagNode.hasNonNull("type") || !tagNode.get("type").isTextual()
            || !tagNode.hasNonNull("label") || !tagNode.get("label").isTextual()) {
            return null;
        }

        TagType tagType;
        try {
            tagType = TagType.valueOf(tagNode.get("type").asText().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        String label = tagNode.get("label").asText();
        if (label.isBlank() || label.length() > LABEL_MAX_LENGTH) {
            return null;
        }

        if (!tagNode.hasNonNull("confidence") || !tagNode.get("confidence").isNumber()) {
            return null;
        }
        BigDecimal confidence = tagNode.get("confidence").decimalValue();
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            return null;
        }

        boolean needsManualCheck = confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;

        return PhotoTag.builder()
            .photo(photo)
            .tagType(tagType)
            .label(label)
            .confidence(confidence)
            .manual(needsManualCheck)
            .build();
    }

    private List<String> extractTextArray(JsonNode result, String fieldName) {
        if (!result.has(fieldName) || !result.get(fieldName).isArray()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (JsonNode item : result.get(fieldName)) {
            if (item.isTextual() && !item.asText().isBlank()) {
                items.add(item.asText());
            }
        }
        return items;
    }

    private String buildPrompt() {
        return """
            당신은 이륜차 사고 현장 사진을 분석해 증빙 자료를 구조화하는 어시스턴트입니다.
            첨부된 사고 사진을 보고, 파손 부위·손상 유형과 주요 객체를 인식해 태깅하세요.

            [태깅 대상]
            - DAMAGE: 파손 부위와 손상 유형 (예: 긁힘, 파손, 찌그러짐)
            - OBJECT: 주요 객체 (예: 번호판, 신호등, 도로 표지판)

            [제약 조건]
            - 사고 원인이나 과실을 추정하는 표현은 절대 사용하지 말 것 — 객체와 손상 상태만 서술할 것
            - 각 태그의 confidence는 0.0~1.0 사이 값으로, 실제 인식 확신도를 정직하게 반영할 것
            - 사진만으로 확인이 더 필요한 부분이 있다면 additionalConfirmationItems에 질문 형태로 나열할 것 (없으면 빈 배열)

            [출력 형식]
            다른 설명 없이 아래 형식의 JSON만 출력하세요.
            {
              "tags": [
                {"type": "DAMAGE", "label": "범퍼 찌그러짐", "confidence": 0.85},
                {"type": "OBJECT", "label": "번호판", "confidence": 0.95}
              ],
              "additionalConfirmationItems": []
            }
            """;
    }
}
