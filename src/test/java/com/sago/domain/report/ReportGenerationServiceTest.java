package com.sago.domain.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.photo.Photo;
import com.sago.domain.photo.PhotoTag;
import com.sago.domain.photo.TagType;
import com.sago.domain.supplementquestion.QuestionRound;
import com.sago.domain.supplementquestion.SupplementQuestion;
import com.sago.domain.user.User;
import com.sago.global.client.gemini.GeminiApiException;
import com.sago.global.client.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 경위서는 체크리스트와 달리 정적 폴백이 정의되어 있지 않다(기획안에 근거 없음).
 * 실패했을 때 빈 Optional을 돌려주는지, 진술과 무관한 내용을 추가하지 않는지가 중요하다.
 */
class ReportGenerationServiceTest {

    private GeminiClient geminiClient;
    private ReportGenerationService generationService;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        generationService = new ReportGenerationService(geminiClient, new ObjectMapper());
    }

    @Test
    @DisplayName("Gemini 호출이 실패하면 빈 Optional을 돌려준다 — 정적 폴백이 없으므로 수동 작성으로 안내해야 한다")
    void returnsEmptyWhenGeminiFails() {
        when(geminiClient.generateContent(anyString())).thenThrow(new GeminiApiException("호출 실패"));

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("응답이 JSON이 아니면 빈 Optional을 돌려준다")
    void returnsEmptyWhenResponseIsNotJson() {
        when(geminiClient.generateContent(anyString())).thenReturn("이건 JSON이 아닙니다");

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("narrative가 비어 있으면 빈 Optional을 돌려준다")
    void returnsEmptyWhenNarrativeIsBlank() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "", "summary": ["요약"], "unverifiedItems": [], "disclaimer": "안내"}
            """);

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("정상 응답이면 narrative·summary·unverifiedItems가 그대로 채워진다")
    void parsesAiResponse() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "2026년 9월 18일, 본인은 직진 중이었습니다.",
             "summary": ["교차로에서 접촉", "본인 경상"],
             "unverifiedItems": ["상대 차량 신호 상태"],
             "disclaimer": "본 문서는 참고 자료입니다."}
            """);

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isPresent();
        assertThat(report.get().getNarrative()).isEqualTo("2026년 9월 18일, 본인은 직진 중이었습니다.");
        assertThat(report.get().getSummary()).containsExactly("교차로에서 접촉", "본인 경상");
        assertThat(report.get().getUnverifiedItems()).containsExactly("상대 차량 신호 상태");
        assertThat(report.get().getDisclaimer()).isEqualTo("본 문서는 참고 자료입니다.");
        assertThat(report.get().getStatus()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    @DisplayName("disclaimer를 안 보내면 기본 문구로 채워진다")
    void fillsDefaultDisclaimerWhenMissing() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": []}
            """);

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isPresent();
        assertThat(report.get().getDisclaimer()).isNotBlank();
    }

    @Test
    @DisplayName("답변이 없는 보완 질문은 프롬프트에 넣지 않는다 — 진술에 없는 내용이 섞이면 안 된다")
    void ignoresUnansweredSupplementQuestions() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": []}
            """);
        SupplementQuestion unanswered = SupplementQuestion.builder()
            .accident(accident())
            .question("신호는 어땠나요?")
            .round(QuestionRound.INITIAL)
            .build();

        generationService.generateReport(accident(), "진술", List.of(unanswered), List.of());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateContent(prompt.capture());
        assertThat(prompt.getValue()).doesNotContain("신호는 어땠나요?");
    }

    @Test
    @DisplayName("사진 태그를 프롬프트에 포함한다")
    void includesPhotoTagsInPrompt() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": []}
            """);

        generationService.generateReport(accident(), "진술", List.of(), List.of(photoTag()));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateContent(prompt.capture());
        assertThat(prompt.getValue()).contains("범퍼 찌그러짐");
    }

    @Test
    @DisplayName("unverifiedItems가 배열이 아니면 형식 오류로 보고 빈 Optional을 돌려준다 — 빈 배열과 구분해야 한다")
    void returnsEmptyWhenUnverifiedItemsIsNotArray() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": "모름"}
            """);

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("disclaimer가 255자를 넘으면 빈 Optional을 돌려준다 — Report.disclaimer 컬럼 길이를 넘으면 저장이 실패한다")
    void returnsEmptyWhenDisclaimerTooLong() {
        String tooLong = "안".repeat(256);
        when(geminiClient.generateContent(anyString())).thenReturn(("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": [], "disclaimer": "%s"}
            """).formatted(tooLong));

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("항목을 만들기만 하고 저장하지는 않는다")
    void doesNotPersist() {
        when(geminiClient.generateContent(anyString())).thenReturn("""
            {"narrative": "본인은 직진 중이었습니다.", "summary": ["교차로에서 접촉"], "unverifiedItems": []}
            """);

        Optional<Report> report = generationService.generateReport(accident(), "진술", List.of(), List.of());

        assertThat(report).isPresent();
        assertThat(report.get().getReportId()).isNull();
    }

    private Accident accident() {
        return Accident.builder()
            .user(User.builder().email("rider@example.com").build())
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build();
    }

    private PhotoTag photoTag() {
        return PhotoTag.builder()
            .photo(Photo.builder().build())
            .tagType(TagType.DAMAGE)
            .label("범퍼 찌그러짐")
            .confidence(new BigDecimal("0.85"))
            .manual(false)
            .build();
    }
}
