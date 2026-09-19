package com.sago.domain.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.domain.photo.PhotoTag;
import com.sago.domain.supplementquestion.SupplementQuestion;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 9 — 사고 기록과 진술을 육하원칙 기반 사고 경위서 초안으로 재구성 (기획안 9.2 Prompt 6).
 * 기획안에 경위서용 정적 폴백이 정의되어 있지 않으므로, Gemini 응답 실패·검증 실패 시에는
 * 빈 Optional을 반환한다 — 호출하는 쪽에서 수동 작성 폼으로 안내해야 한다(기획안 10절 예외처리).
 *
 * 저장은 하지 않는다. Gemini 호출이 트랜잭션 안에 들어가면 응답이 올 때까지 DB 커넥션을
 * 붙잡게 되므로, 저장은 호출자가 짧은 트랜잭션으로 처리한다(#48의 ChecklistGenerationService와
 * 같은 이유).
 */
@Service
public class ReportGenerationService {

    private static final int SUMMARY_MAX_LINES = 5;
    private static final int UNVERIFIED_ITEM_MAX_LINES = 10;
    private static final int LINE_MAX_LENGTH = 255;
    private static final String DEFAULT_DISCLAIMER = "본 문서는 사용자 진술을 기반으로 AI가 정리한 자료입니다.";

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ReportGenerationService(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 경위서 초안을 만들어 돌려준다. 외부 호출(Gemini)이 들어 있으므로 트랜잭션 밖에서 부를 것.
     */
    public Optional<Report> generateReport(Accident accident, String statementText,
                                            List<SupplementQuestion> supplementQuestions,
                                            List<PhotoTag> photoTags) {
        String responseText;
        try {
            responseText = geminiClient.generateContent(
                buildPrompt(accident, statementText, supplementQuestions, photoTags));
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

        String narrative = readText(result, "narrative");
        if (narrative == null || narrative.isBlank()) {
            return Optional.empty();
        }

        Optional<List<String>> summaryLines = readLines(result, "summary");
        if (summaryLines.isEmpty()) {
            return Optional.empty();
        }
        List<String> summary = summaryLines.get();
        if (summary.isEmpty() || summary.size() > SUMMARY_MAX_LINES) {
            return Optional.empty();
        }

        // 항목 하나가 형식에 안 맞는다고 그 항목만 빼거나 잘라내지 않는다. unverifiedItems는
        // "이건 추정이다"라는 경고라, 경고가 조용히 사라지면 추정이 사실처럼 읽히게 된다.
        // 잘라내도 문장이 중간에 끊겨 의미가 바뀔 수 있어, 통째로 실패시키는 쪽이 더 안전하다.
        Optional<List<String>> unverifiedItemLines = readLines(result, "unverifiedItems");
        if (unverifiedItemLines.isEmpty()) {
            return Optional.empty();
        }
        List<String> unverifiedItems = unverifiedItemLines.get();
        if (unverifiedItems.size() > UNVERIFIED_ITEM_MAX_LINES) {
            return Optional.empty();
        }

        String disclaimer = readText(result, "disclaimer");
        if (disclaimer == null || disclaimer.isBlank()) {
            disclaimer = DEFAULT_DISCLAIMER;
        } else if (disclaimer.length() > LINE_MAX_LENGTH) {
            // Report.disclaimer 컬럼이 255자라, 넘으면 저장 단계에서 실패한다. 여기서 먼저 걸러낸다.
            return Optional.empty();
        }

        return Optional.of(Report.builder()
            .accident(accident)
            .narrative(narrative)
            .summary(summary)
            .unverifiedItems(unverifiedItems)
            .disclaimer(disclaimer)
            .build());
    }

    private String readText(JsonNode result, String fieldName) {
        return result.hasNonNull(fieldName) && result.get(fieldName).isTextual()
            ? result.get(fieldName).asText()
            : null;
    }

    /**
     * 문자열 배열 필드를 읽는다. 필드가 없거나 배열이 아니거나 항목 형식이 잘못됐으면
     * 빈 Optional을 돌려준다 — "정상적으로 비어 있음"과 "형식이 잘못됨"을 구분해야
     * unverifiedItems처럼 빈 배열이 유효한 필드에서 형식 오류를 빈 배열로 잘못 넘기지 않는다.
     */
    private Optional<List<String>> readLines(JsonNode result, String fieldName) {
        if (!result.has(fieldName) || !result.get(fieldName).isArray()) {
            return Optional.empty();
        }

        List<String> lines = new ArrayList<>();
        for (JsonNode item : result.get(fieldName)) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > LINE_MAX_LENGTH) {
                return Optional.empty();
            }
            lines.add(item.asText());
        }
        return Optional.of(lines);
    }

    private String buildPrompt(Accident accident, String statementText,
                                List<SupplementQuestion> supplementQuestions, List<PhotoTag> photoTags) {
        return """
            당신은 이륜차 사고 진술과 기록을 바탕으로 육하원칙에 따른 사고 경위서를 작성하는 어시스턴트입니다.

            [사고 기록]
            - 사고 유형: %s
            - 발생 시각: %s
            - 진행 방향: %s
            - 신호·도로 상태: %s

            [사용자 음성 진술]
            %s

            [보완 질문과 답변]
            %s

            [사진 태그]
            %s

            [제약 조건]
            - 사용자가 진술하지 않은 사실을 추가하거나 추정하지 말 것(환각 절대 금지)
            - 과실·책임을 판단하는 표현을 쓰지 말 것
            - 불명확한 부분은 "확인되지 않음"으로 명시할 것
            - narrative는 시간 순으로 서술하고, 존댓말 공문서 문체로 작성할 것
            - summary는 핵심을 3줄 내외로 요약할 것
            - unverifiedItems는 진술만으로 확인되지 않는 항목을 나열하고, 없으면 빈 배열로 둘 것

            [출력 형식]
            다른 설명 없이 아래 형식의 JSON만 출력하세요.
            {"narrative": "...", "summary": ["...", "..."], "unverifiedItems": ["..."], "disclaimer": "..."}
            """.formatted(
            accident.getAccidentType(),
            accident.getOccurredAt(),
            accident.getDirection() != null ? accident.getDirection() : "정보 없음",
            accident.getRoadCondition() != null ? accident.getRoadCondition() : "정보 없음",
            statementText,
            formatSupplementQuestions(supplementQuestions),
            formatPhotoTags(photoTags)
        );
    }

    private String formatSupplementQuestions(List<SupplementQuestion> supplementQuestions) {
        List<String> answered = supplementQuestions == null ? List.of() : supplementQuestions.stream()
            .filter(question -> question.getAnswer() != null && !question.getAnswer().isBlank())
            .map(question -> "- Q: %s A: %s".formatted(question.getQuestion(), question.getAnswer()))
            .toList();
        return answered.isEmpty() ? "보완 질문 없음" : String.join("\n", answered);
    }

    private String formatPhotoTags(List<PhotoTag> photoTags) {
        List<String> tags = photoTags == null ? List.of() : photoTags.stream()
            .map(tag -> "- %s %s (신뢰도 %s)".formatted(tag.getTagType(), tag.getLabel(), tag.getConfidence()))
            .toList();
        return tags.isEmpty() ? "사진 없음" : String.join("\n", tags);
    }
}
