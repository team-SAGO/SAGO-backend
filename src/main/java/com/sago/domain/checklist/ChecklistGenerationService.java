package com.sago.domain.checklist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.accident.InjuryLevel;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 3 — 사고 유형별 AI 대응 체크리스트 생성 (기획안 9.2 Prompt 1).
 * Gemini 응답 실패·무료 한도 초과 시 사고 유형별 정적 기본 체크리스트로 폴백한다.
 */
@Service
public class ChecklistGenerationService {

    private static final Map<AccidentType, List<String>> STATIC_CHECKLISTS = Map.of(
        AccidentType.PERSONAL, List.of("119·112 긴급 신고", "부상자 응급조치", "2차 사고 방지"),
        AccidentType.VEHICLE, List.of("부상·안전 확보", "상대 정보 교환 안내", "현장 보존"),
        AccidentType.SINGLE, List.of("안전 지대로 이동", "차량·시설 파손 확인", "주변 CCTV 확인"),
        AccidentType.HIT_AND_RUN, List.of("112 신고 최우선", "차량번호 확보", "블랙박스·목격자 확보 안내")
    );

    private final GeminiClient geminiClient;
    private final ChecklistItemRepository checklistItemRepository;
    private final ObjectMapper objectMapper;

    public ChecklistGenerationService(GeminiClient geminiClient,
                                       ChecklistItemRepository checklistItemRepository,
                                       ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.checklistItemRepository = checklistItemRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ChecklistItem> generateChecklist(Accident accident) {
        List<String> contents;
        ChecklistSource source;

        try {
            String responseText = geminiClient.generateContent(buildPrompt(accident));
            contents = parseItems(responseText);
            source = ChecklistSource.AI;
        } catch (GeminiApiException | JsonProcessingException e) {
            contents = STATIC_CHECKLISTS.getOrDefault(accident.getAccidentType(), List.of());
            source = ChecklistSource.STATIC;
        }

        List<ChecklistItem> checklistItems = new ArrayList<>();
        int orderNo = 1;
        for (String content : contents) {
            checklistItems.add(ChecklistItem.builder()
                .accident(accident)
                .content(content)
                .orderNo(orderNo++)
                .source(source)
                .build());
        }

        return checklistItemRepository.saveAll(checklistItems);
    }

    private String buildPrompt(Accident accident) {
        return """
            당신은 이륜차 사고 대응 체크리스트를 생성하는 어시스턴트입니다.
            아래 사고 정보를 바탕으로, 사고 직후 즉시 수행해야 할 행동 목록을 우선순위 순으로 생성하세요.

            [사고 정보]
            - 사고 유형: %s
            - 본인 부상 정도: %s
            - 상대방 부상 정도: %s
            - 위치 맥락: %s

            [제약 조건]
            - 대인사고 또는 뺑소니 사고인 경우, 119·112 신고 항목을 반드시 최상위에 고정할 것
            - 법률적·과실 판단 표현은 절대 사용하지 말 것
            - 각 항목은 15자 내외의 명령형 문장으로 작성할 것
            - 안전 관련 항목을 최우선으로 배치할 것
            - 항목은 5개 이내로 작성할 것

            [출력 형식]
            다른 설명 없이, 문자열 배열 형태의 JSON만 출력하세요.
            예: ["119 신고하기", "안전지대로 이동", "사고 현장 촬영"]
            """.formatted(
            koreanLabel(accident.getAccidentType()),
            koreanLabel(accident.getInjurySelf()),
            koreanLabel(accident.getInjuryOther()),
            accident.getRoadCondition() != null ? accident.getRoadCondition() : "정보 없음"
        );
    }

    private List<String> parseItems(String responseText) throws JsonProcessingException {
        String json = responseText
            .replaceAll("(?s)```json\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .trim();
        return objectMapper.readValue(json, new TypeReference<List<String>>() {
        });
    }

    private String koreanLabel(AccidentType type) {
        return switch (type) {
            case PERSONAL -> "대인사고";
            case VEHICLE -> "차량 간 사고";
            case SINGLE -> "단독 사고";
            case HIT_AND_RUN -> "뺑소니 사고";
        };
    }

    private String koreanLabel(InjuryLevel level) {
        if (level == null) {
            return "정보 없음";
        }
        return switch (level) {
            case NONE -> "없음";
            case MINOR -> "경상";
            case SEVERE -> "중상";
        };
    }
}
