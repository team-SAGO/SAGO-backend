package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentRepository;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 권한과 HTTP 계층을 확인한다.
 *
 * 업로드 서비스는 대체한다. 실제로 부르면 S3 버킷과 Google STT가 필요한데 테스트 환경에는
 * 둘 다 없다. 여기서 보려는 것은 "누가 어느 사고에 진술을 남길 수 있는가"이고,
 * 업로드·인식 로직은 각자의 테스트에서 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private StatementUploadService statementUploadService;

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

        accident = accidentRepository.save(Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build());
    }

    @Test
    @DisplayName("사고 주인이 음성을 올리면 201과 함께 인식 결과가 나온다")
    void ownerRecordsStatement() throws Exception {
        when(statementUploadService.upload(any(Accident.class), any(MultipartFile.class)))
            .thenAnswer(invocation -> statementRepository.save(Statement.builder()
                .accident(invocation.getArgument(0))
                .audioFileUrl("https://bucket/statements/audio/a.m4a")
                .sttText("신호 대기 중 추돌당했습니다")
                .build()));

        mockMvc.perform(multipart("/api/accidents/{id}/statements", accident.getAccidentId())
                .file(audio())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sttText").value("신호 대기 중 추돌당했습니다"))
            .andExpect(jsonPath("$.transcribed").value(true));
    }

    @Test
    @DisplayName("인식에 실패해도 201이고, transcribed로 실패를 알린다")
    void recognitionFailureIsStillCreated() throws Exception {
        when(statementUploadService.upload(any(Accident.class), any(MultipartFile.class)))
            .thenAnswer(invocation -> statementRepository.save(Statement.builder()
                .accident(invocation.getArgument(0))
                .audioFileUrl("https://bucket/statements/audio/a.m4a")
                .sttText(null)
                .build()));

        mockMvc.perform(multipart("/api/accidents/{id}/statements", accident.getAccidentId())
                .file(audio())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transcribed").value(false))
            .andExpect(jsonPath("$.sttText").doesNotExist());
    }

    @Test
    @DisplayName("응답에 원본 음성 주소가 들어가지 않는다")
    void responseDoesNotExposeAudioUrl() throws Exception {
        when(statementUploadService.upload(any(Accident.class), any(MultipartFile.class)))
            .thenAnswer(invocation -> statementRepository.save(Statement.builder()
                .accident(invocation.getArgument(0))
                .audioFileUrl("https://bucket/statements/audio/a.m4a")
                .sttText("내용")
                .build()));

        // 주소에 S3 오브젝트 키가 담겨 있어 버킷 내부 경로가 드러난다.
        mockMvc.perform(multipart("/api/accidents/{id}/statements", accident.getAccidentId())
                .file(audio())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(jsonPath("$.audioFileUrl").doesNotExist());
    }

    @Test
    @DisplayName("남의 사고에는 진술을 올릴 수 없고, 업로드도 일어나지 않는다")
    void strangerCannotRecordAndNothingIsUploaded() throws Exception {
        mockMvc.perform(multipart("/api/accidents/{id}/statements", accident.getAccidentId())
                .file(audio())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));

        // 소유권을 확인하기 전에 S3에 올려버리면 남의 사고 경로에 파일이 남는다.
        verify(statementUploadService, never()).upload(any(), any());
    }

    @Test
    @DisplayName("토큰 없이 진술을 올릴 수 없다")
    void recordRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/accidents/{id}/statements", accident.getAccidentId())
                .file(audio()))
            .andExpect(status().isUnauthorized());

        verify(statementUploadService, never()).upload(any(), any());
    }

    @Test
    @DisplayName("진술 목록은 먼저 남긴 순서로 나온다")
    void listsStatementsInRecordedOrder() throws Exception {
        statementRepository.save(Statement.builder()
            .accident(accident).audioFileUrl("https://bucket/1.m4a").sttText("첫 번째 진술").build());
        statementRepository.save(Statement.builder()
            .accident(accident).audioFileUrl("https://bucket/2.m4a").sttText(null).build());

        mockMvc.perform(get("/api/accidents/{id}/statements", accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].sttText").value("첫 번째 진술"))
            .andExpect(jsonPath("$[1].transcribed").value(false));
    }

    @Test
    @DisplayName("남의 사고 진술 목록은 404다")
    void strangerCannotListStatements() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}/statements", accident.getAccidentId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound());
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile("audio", "statement.m4a", "audio/mp4", new byte[] {1, 2, 3});
    }
}
