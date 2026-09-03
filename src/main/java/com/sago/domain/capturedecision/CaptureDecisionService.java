package com.sago.domain.capturedecision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Step 5 — 진술을 기반으로 AR 촬영·Vision 태깅 진행 필요 여부를 AI가 판단 (기획안 9.2 Prompt 3).
 * 판단이 모호하거나 5초 내 응답이 없으면 아무것도 저장하지 않고 빈 Optional을 반환한다 —
 * 호출하는 쪽에서 사용자가 직접 선택하는 UI로 전환해야 한다(건너뛴 경우에도 수동 진입 경로 유지).
 */
@Service
public class CaptureDecisionService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int REASON_MAX_LENGTH = 255;

    private final GeminiClient geminiClient;
    private final CaptureDecisionRepository captureDecisionRepository;
    private final ObjectMapper objectMapper;

    public CaptureDecisionService(GeminiClient geminiClient,
                                   CaptureDecisionRepository captureDecisionRepository,
                                   ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.captureDecisionRepository = captureDecisionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<CaptureDecision> decideByAi(Accident accident, String statementText) {
        String prompt = buildPrompt(accident, statementText);

        String responseText;
        try {
            responseText = geminiClient.generateContent(prompt, TIMEOUT);
        } catch (GeminiApiException e) {
            return Optional.empty();
        }

        JsonNode result;
        try {
            String json = responseText
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
            result = objectMapper.readTree(json);
        } catch (Exception e) {
            return Optional.empty();
        }

        if (!result.hasNonNull("required") || !result.get("required").isBoolean()) {
            return Optional.empty();
        }

        boolean required = result.get("required").asBoolean();
        String reason = buildReason(result);

        CaptureDecision decision = CaptureDecision.builder()
            .accident(accident)
            .required(required)
            .reason(reason)
            .decidedBy(DecidedBy.AI)
            .build();

        return Optional.of(captureDecisionRepository.save(decision));
    }

    private String buildReason(JsonNode result) {
        String reason = result.hasNonNull("reason") ? result.get("reason").asText() : "";

        List<String> recommendedPhotos = List.of();
        if (result.has("recommendedPhotos") && result.get("recommendedPhotos").isArray()) {
            JsonNode photosNode = result.get("recommendedPhotos");
            boolean allTextual = true;
            for (JsonNode photo : photosNode) {
                if (!photo.isTextual()) {
                    allTextual = false;
                    break;
                }
            }
            if (allTextual) {
                recommendedPhotos = objectMapper.convertValue(
                    photosNode, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class));
            }
        }

        String combined = recommendedPhotos.isEmpty()
            ? reason
            : reason + " (권장 촬영: " + recommendedPhotos.stream().collect(Collectors.joining(", ")) + ")";

        return combined.length() > REASON_MAX_LENGTH
            ? combined.substring(0, REASON_MAX_LENGTH)
            : combined;
    }

    private String buildPrompt(Accident accident, String statementText) {
        return """
            당신은 이륜차 사고 대응 서비스에서, 사고 현장 촬영(AR 가이드)과 AI Vision 태깅
            진행이 필요한지 판단하는 어시스턴트입니다.

            [사고 정보]
            - 사고 유형: %s
            - 사고 기록: 발생 시각 %s, 진행 방향 %s, 도로 상태 %s

            [사용자 음성 진술 텍스트]
            %s

            [판단 기준]
            - 진술만으로 사고 상황이 충분히 설명되고 별도 시각 증빙이 불필요하면 촬영 불필요로 판단
            - 파손 부위, 상대 차량 번호판, 노면 흔적 등 시각적 증빙이 사고 처리에 중요할 것으로 보이면 촬영 필요로 판단
            - 판단이 애매하면 required를 true로 두어 사용자가 놓치지 않도록 할 것

            [출력 형식]
            다른 설명 없이 아래 형식의 JSON만 출력하세요.
            {"required": true, "reason": "판단 근거를 한두 문장으로", "recommendedPhotos": ["번호판", "파손 부위"]}
            """.formatted(
            accident.getAccidentType(),
            accident.getOccurredAt(),
            accident.getDirection() != null ? accident.getDirection() : "정보 없음",
            accident.getRoadCondition() != null ? accident.getRoadCondition() : "정보 없음",
            statementText
        );
    }
}
