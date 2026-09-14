package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.user.User;
import com.sago.global.client.stt.SpeechToTextClient;
import com.sago.global.client.stt.SpeechToTextException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 음성 인식 결과에 따라 무엇이 저장되는지 확인한다.
 * 인식에 실패해도 원본 음성은 보존돼야 한다는 것이 기획안의 예외 처리 요구다.
 */
class StatementServiceTest {

    private static final String AUDIO_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/statements/audio/a.m4a";
    private static final byte[] AUDIO = {1, 2, 3};

    private SpeechToTextClient speechToTextClient;
    private StatementRepository statementRepository;
    private StatementService statementService;
    private Accident accident;

    @BeforeEach
    void setUp() {
        speechToTextClient = mock(SpeechToTextClient.class);
        statementRepository = mock(StatementRepository.class);
        statementService = new StatementService(speechToTextClient, statementRepository);

        when(statementRepository.save(any(Statement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        accident = Accident.builder()
            .user(User.builder().email("rider@example.com").build())
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("인식에 성공하면 텍스트와 원본 음성 주소가 함께 저장된다")
    void savesRecognizedText() {
        when(speechToTextClient.transcribe(AUDIO)).thenReturn("신호 대기 중 뒤에서 추돌당했습니다");

        Statement statement = statementService.transcribe(accident, AUDIO_URL, AUDIO);

        assertThat(statement.getSttText()).isEqualTo("신호 대기 중 뒤에서 추돌당했습니다");
        assertThat(statement.getAudioFileUrl()).isEqualTo(AUDIO_URL);
    }

    @Test
    @DisplayName("인식에 실패해도 원본 음성은 저장되고 텍스트만 비워진다")
    void keepsAudioWhenRecognitionFails() {
        when(speechToTextClient.transcribe(AUDIO))
            .thenThrow(new SpeechToTextException("음성 인식 결과가 없습니다"));

        Statement statement = statementService.transcribe(accident, AUDIO_URL, AUDIO);

        // 재녹음이나 직접 입력으로 이어갈 수 있도록 음성은 남아야 한다.
        assertThat(statement.getAudioFileUrl()).isEqualTo(AUDIO_URL);
        assertThat(statement.getSttText()).isNull();
    }
}
