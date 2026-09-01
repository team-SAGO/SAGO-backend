package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
import com.sago.global.client.stt.SpeechToTextClient;
import com.sago.global.client.stt.SpeechToTextException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4 — 음성 진술을 저장하고 STT로 텍스트 변환한다.
 * 음성 인식 실패 시 원본 음성은 그대로 저장하고 sttText만 비워둔다
 * (기획안 예외처리: 음성 인식 실패 시 재녹음 또는 직접 입력 기능 제공).
 */
@Service
public class StatementService {

    private final SpeechToTextClient speechToTextClient;
    private final StatementRepository statementRepository;

    public StatementService(SpeechToTextClient speechToTextClient, StatementRepository statementRepository) {
        this.speechToTextClient = speechToTextClient;
        this.statementRepository = statementRepository;
    }

    @Transactional
    public Statement transcribe(Accident accident, String audioFileUrl, byte[] audioBytes) {
        String sttText = null;
        try {
            sttText = speechToTextClient.transcribe(audioBytes);
        } catch (SpeechToTextException e) {
            // 원본 음성은 보존하고, 텍스트는 비워둔 채 저장 — 사용자가 재녹음하거나 직접 입력하도록 유도
        }

        Statement statement = Statement.builder()
            .accident(accident)
            .audioFileUrl(audioFileUrl)
            .sttText(sttText)
            .build();

        return statementRepository.save(statement);
    }
}
