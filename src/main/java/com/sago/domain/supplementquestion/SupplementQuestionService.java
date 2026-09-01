package com.sago.domain.supplementquestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Step 4 — 음성 진술에서 육하원칙 누락 요소를 분석해 보완 질문을 생성 (기획안 9.2 Prompt 2).
 * Gemini 응답 실패·검증 실패 시에는(기획안에 정적 폴백이 정의되어 있지 않으므로)
 * 보완 질문 없이 빈 리스트를 반환한다 — 원본 진술은 이미 저장되어 있으므로 플로우를 막지 않는다.
 */
@Service
public class SupplementQuestionService {

    private static final int MAX_QUESTIONS = 3;

    private final GeminiClient geminiClient;
    private final SupplementQuestionRepository supplementQuestionRepository;
    private final ObjectMapper objectMapper;

    public SupplementQuestionService(GeminiClient geminiClient,
                                      SupplementQuestionRepository supplementQuestionRepository,
                                      ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.supplementQuestionRepository = supplementQuestionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<SupplementQuestion> generateQuestions(Accident accident, String statementText,
                                                        QuestionRound round, List<String> additionalContext) {
        List<String> questions;
        try {
            String responseText = geminiClient.generateContent(
                buildPrompt(accident, statementText, additionalContext));
            questions = parseItems(responseText);
            validateItems(questions);
        } catch (GeminiApiException | JsonProcessingException | IllegalArgumentException e) {
            return List.of();
        }

        List<SupplementQuestion> supplementQuestions = questions.stream()
            .map(question -> SupplementQuestion.builder()
                .accident(accident)
                .question(question)
                .round(round)
                .build())
            .toList();

        return supplementQuestionRepository.saveAll(supplementQuestions);
    }

    private String buildPrompt(Accident accident, String statementText, List<String> additionalContext) {
        String knownFacts = """
            - 사고 유형: %s
            - 발생 시각: %s
            - 사고 위치: %s
            - 진행 방향: %s
            - 신호·도로 상태: %s""".formatted(
            accident.getAccidentType(),
            accident.getOccurredAt(),
            accident.getLatitude() != null && accident.getLongitude() != null
                ? "위도 %s, 경도 %s".formatted(accident.getLatitude(), accident.getLongitude())
                : "정보 없음",
            accident.getDirection() != null ? accident.getDirection() : "정보 없음",
            accident.getRoadCondition() != null ? accident.getRoadCondition() : "정보 없음"
        );

        String additionalSection = additionalContext == null || additionalContext.isEmpty()
            ? ""
            : "\n\n[사진 태깅 후 추가로 확인이 필요한 항목]\n- " + String.join("\n- ", additionalContext);

        return """
            당신은 이륜차 사고 진술서를 검토해 육하원칙(언제/어디서/누가/무엇을/어떻게/왜) 기준으로
            누락되거나 모호한 부분을 찾아 보완 질문을 만드는 어시스턴트입니다.

            [이미 확보된 사고 정보 — 이 내용은 절대 다시 질문하지 마세요]
            %s

            [사용자 음성 진술 텍스트]
            %s
            %s

            [제약 조건]
            - 위 "이미 확보된 사고 정보"나 진술에 이미 답변된 내용은 절대 재질문하지 말 것
            - 유도 질문 없이 중립적인 표현으로 작성할 것
            - 질문은 최대 3개까지만 생성할 것
            - 진짜 누락된 정보가 없다면 빈 배열을 반환할 것

            [출력 형식]
            다른 설명 없이, 문자열 배열 형태의 JSON만 출력하세요.
            예: ["상대 차량의 진행 방향은 어땠나요?", "신호는 어떤 상태였나요?"]
            """.formatted(knownFacts, statementText, additionalSection);
    }

    private List<String> parseItems(String responseText) throws JsonProcessingException {
        String json = responseText
            .replaceAll("(?s)```json\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .trim();
        return objectMapper.readValue(json, new TypeReference<List<String>>() {
        });
    }

    private void validateItems(List<String> items) {
        if (items == null || items.size() > MAX_QUESTIONS
            || items.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > 255)) {
            throw new IllegalArgumentException("유효하지 않은 Gemini 보완 질문 응답");
        }
    }
}
