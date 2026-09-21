package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentRepository;
import com.sago.domain.accident.AccidentStatus;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.statement.Statement;
import com.sago.domain.statement.StatementRepository;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경위서 저장·조회·확정 (#86).
 *
 * 생성 서비스는 대체한다. 실제로 부르면 Gemini가 필요하고, 여기서 보려는 것은 "누가 어느 사고의
 * 경위서를 다룰 수 있는가"와 "생성 결과를 어떻게 저장·확정하는가"다. 생성 자체는 #78에서 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    private static final String ENDPOINT = "/api/accidents/{id}/report";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReportGenerationService reportGenerationService;

    private String ownerToken;
    private String strangerToken;
    private Accident accident;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        ownerToken = jwtTokenProvider.createAccessToken(owner.getUserId());
        strangerToken = jwtTokenProvider.createAccessToken(stranger.getUserId());

        accident = accidentRepository.saveAndFlush(Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now().minusMinutes(30))
            .build());
    }

    @Test
    @DisplayName("AI가 만든 경위서가 저장되고 201로 나간다")
    void savesGeneratedReport() throws Exception {
        generationReturns("신호 대기 중 뒤차와 추돌했습니다.");

        mockMvc.perform(write().content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.narrative").value("신호 대기 중 뒤차와 추돌했습니다."))
            .andExpect(jsonPath("$.summary[0]").value("요약 한 줄"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.pdfReady").value(false))
            .andExpect(jsonPath("$.confirmedAt").doesNotExist());

        assertThat(reportRepository.findByAccident_AccidentId(accident.getAccidentId())).isPresent();
    }

    @Test
    @DisplayName("다시 생성하면 새 행을 만들지 않고 내용을 갈아끼우며 version을 올린다")
    void regenerationReplacesContent() throws Exception {
        generationReturns("첫 번째 초안");
        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());

        generationReturns("두 번째 초안");
        mockMvc.perform(write().content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.narrative").value("두 번째 초안"))
            .andExpect(jsonPath("$.version").value(2));

        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("진술이 여러 건이면 시간 순으로 이어 붙여 생성에 넘긴다")
    void joinsStatementsInOrder() throws Exception {
        statementRepository.saveAndFlush(statement("먼저 한 진술"));
        statementRepository.saveAndFlush(statement("나중에 한 진술"));
        // 인식에 실패한 진술은 텍스트가 없어 빠져야 한다
        statementRepository.saveAndFlush(statement(null));
        generationReturns("초안");

        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());

        ArgumentCaptor<String> statementText = ArgumentCaptor.forClass(String.class);
        verify(reportGenerationService)
            .generateReport(any(), statementText.capture(), anyList(), anyList());
        assertThat(statementText.getValue())
            .contains("먼저 한 진술")
            .contains("나중에 한 진술");
        assertThat(statementText.getValue().indexOf("먼저 한 진술"))
            .isLessThan(statementText.getValue().indexOf("나중에 한 진술"));
    }

    @Test
    @DisplayName("AI 생성에 실패하면 502로 알리고 직접 작성을 안내한다")
    void generationFailureIsReported() throws Exception {
        when(reportGenerationService.generateReport(any(), anyString(), anyList(), anyList()))
            .thenReturn(Optional.empty());

        mockMvc.perform(write().content("{}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("REPORT_GENERATION_FAILED"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("직접 작성")));

        assertThat(reportRepository.count()).isZero();
    }

    @Test
    @DisplayName("본문을 직접 보내면 AI를 부르지 않고 그대로 저장한다")
    void savesUserWrittenReport() throws Exception {
        mockMvc.perform(write().content("{\"narrative\":\"제가 직접 적은 경위입니다.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.narrative").value("제가 직접 적은 경위입니다."))
            .andExpect(jsonPath("$.summary").isEmpty())
            .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("직접 작성")));

        verify(reportGenerationService, never()).generateReport(any(), anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("확정 전에는 본문을 고칠 수 있고 version이 올라간다")
    void updatesNarrativeBeforeConfirm() throws Exception {
        generationReturns("AI 초안");
        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());

        mockMvc.perform(patch(ENDPOINT, accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"narrative\":\"직접 고친 본문\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.narrative").value("직접 고친 본문"))
            .andExpect(jsonPath("$.version").value(2))
            // 요약은 AI가 뽑은 그대로 둔다
            .andExpect(jsonPath("$.summary[0]").value("요약 한 줄"));
    }

    @Test
    @DisplayName("확정하면 경위서가 CONFIRMED가 되고 사고도 함께 끝난다")
    void confirmCompletesAccident() throws Exception {
        generationReturns("AI 초안");
        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT + "/confirm", accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        // 경위서 확정이 곧 사고 처리 종료다 (#34)
        assertThat(accidentRepository.findById(accident.getAccidentId()).orElseThrow().getStatus())
            .isEqualTo(AccidentStatus.COMPLETED);
    }

    @Test
    @DisplayName("확정을 두 번 해도 오류가 아니다")
    void confirmIsIdempotent() throws Exception {
        generationReturns("AI 초안");
        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());

        mockMvc.perform(confirm()).andExpect(status().isOk());
        mockMvc.perform(confirm())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("확정된 경위서는 수정도 재생성도 되지 않는다")
    void confirmedReportIsImmutable() throws Exception {
        generationReturns("AI 초안");
        mockMvc.perform(write().content("{}")).andExpect(status().isCreated());
        mockMvc.perform(confirm()).andExpect(status().isOk());

        mockMvc.perform(patch(ENDPOINT, accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"narrative\":\"확정 후 수정 시도\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REPORT_ALREADY_CONFIRMED"));

        mockMvc.perform(write().content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REPORT_ALREADY_CONFIRMED"));
    }

    @Test
    @DisplayName("경위서가 없으면 조회·확정 모두 404다")
    void missingReportIsNotFound() throws Exception {
        mockMvc.perform(get(ENDPOINT, accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

        mockMvc.perform(confirm())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 사고 경위서는 만들 수도 볼 수도 없다")
    void strangerCannotTouchReport() throws Exception {
        mockMvc.perform(post(ENDPOINT, accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));

        mockMvc.perform(get(ENDPOINT, accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound());

        verify(reportGenerationService, never()).generateReport(any(), anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("토큰 없이 경위서를 만들 수 없다")
    void writeRequiresAuthentication() throws Exception {
        mockMvc.perform(post(ENDPOINT, accident.getAccidentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder write() {
        return post(ENDPOINT, accident.getAccidentId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirm() {
        return post(ENDPOINT + "/confirm", accident.getAccidentId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken);
    }

    private void generationReturns(String narrative) {
        when(reportGenerationService.generateReport(any(), anyString(), anyList(), anyList()))
            .thenAnswer(invocation -> Optional.of(Report.builder()
                .accident(invocation.getArgument(0))
                .narrative(narrative)
                .summary(List.of("요약 한 줄"))
                .unverifiedItems(List.of("상대 차량 번호"))
                .disclaimer("본 문서는 사용자 진술을 기반으로 AI가 정리한 자료입니다.")
                .build()));
    }

    private Statement statement(String sttText) {
        return Statement.builder()
            .accident(accident)
            .audioFileUrl("https://bucket/statements/audio/" + System.nanoTime() + ".wav")
            .sttText(sttText)
            .build();
    }
}
