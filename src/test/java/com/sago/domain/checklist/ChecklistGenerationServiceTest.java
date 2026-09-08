package com.sago.domain.checklist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.user.User;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gemini가 실패했을 때 사용자가 무엇을 받는지 확인한다.
 *
 * 체크리스트는 사고 직후 첫 화면이라, 빈 목록이 내려가면 사용자는 다음에 뭘 해야 할지
 * 알 수 없는 상태가 된다. 폴백이 실제로 비어 있지 않은지가 중요하다.
 */
class ChecklistGenerationServiceTest {

    private GeminiClient geminiClient;
    private ChecklistGenerationService generationService;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        generationService = new ChecklistGenerationService(geminiClient, new ObjectMapper());
    }

    @ParameterizedTest
    @EnumSource(AccidentType.class)
    @DisplayName("Gemini가 실패해도 모든 사고 유형에서 빈 체크리스트가 나오지 않는다")
    void staticFallbackCoversEveryAccidentType(AccidentType type) {
        when(geminiClient.generateContent(anyString()))
            .thenThrow(new GeminiApiException("호출 실패"));

        List<ChecklistItem> items = generationService.generateChecklist(accident(type));

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> {
            assertThat(item.getSource()).isEqualTo(ChecklistSource.STATIC);
            assertThat(item.getContent()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Gemini 응답이 깨져 있어도 정적 목록으로 폴백한다")
    void malformedResponseFallsBackToStatic() {
        when(geminiClient.generateContent(anyString())).thenReturn("이건 JSON이 아닙니다");

        List<ChecklistItem> items = generationService.generateChecklist(accident(AccidentType.VEHICLE));

        assertThat(items).isNotEmpty();
        assertThat(items.get(0).getSource()).isEqualTo(ChecklistSource.STATIC);
    }

    @Test
    @DisplayName("정상 응답이면 AI 항목이 순서대로 만들어진다")
    void parsesAiResponse() {
        when(geminiClient.generateContent(anyString()))
            .thenReturn("[\"119 신고하기\", \"안전지대로 이동\"]");

        List<ChecklistItem> items = generationService.generateChecklist(accident(AccidentType.PERSONAL));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getContent()).isEqualTo("119 신고하기");
        assertThat(items.get(0).getOrderNo()).isEqualTo(1);
        assertThat(items.get(1).getOrderNo()).isEqualTo(2);
        assertThat(items.get(0).getSource()).isEqualTo(ChecklistSource.AI);
    }

    @Test
    @DisplayName("항목을 만들기만 하고 저장하지는 않는다")
    void doesNotPersist() {
        when(geminiClient.generateContent(anyString()))
            .thenReturn("[\"119 신고하기\"]");

        List<ChecklistItem> items = generationService.generateChecklist(accident(AccidentType.PERSONAL));

        // 저장은 호출자가 짧은 트랜잭션에서 한다. 여기서 저장하면 Gemini 호출이
        // 트랜잭션 안에 들어가 30초까지 DB 커넥션을 붙잡게 된다.
        assertThat(items).allSatisfy(item -> assertThat(item.getChecklistItemId()).isNull());
    }

    private Accident accident(AccidentType type) {
        return Accident.builder()
            .user(User.builder().email("rider@example.com").build())
            .accidentType(type)
            .occurredAt(LocalDateTime.now())
            .build();
    }
}
