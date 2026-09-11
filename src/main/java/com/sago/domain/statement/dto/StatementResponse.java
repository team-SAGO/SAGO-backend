package com.sago.domain.statement.dto;

import com.sago.domain.statement.Statement;

import java.time.LocalDateTime;

/**
 * 음성 진술 하나.
 *
 * 원본 음성 파일의 주소는 내려주지 않는다. S3 오브젝트 키가 담긴 주소라 버킷 내부 경로가
 * 드러나고, 버킷이 비공개라 그 주소로는 어차피 열리지 않는다. 녹음을 다시 들어야 하면
 * 만료 시간이 있는 주소를 따로 발급해야 한다.
 *
 * @param sttText     인식된 텍스트. 인식에 실패했으면 null이다.
 * @param transcribed 음성 인식 성공 여부. false면 원본 음성은 보존됐지만 텍스트가 없는 상태라,
 *                    클라이언트가 재녹음이나 직접 입력을 안내해야 한다.
 */
public record StatementResponse(
    Long statementId,
    String sttText,
    boolean transcribed,
    LocalDateTime createdAt
) {

    public static StatementResponse from(Statement statement) {
        return new StatementResponse(
            statement.getStatementId(),
            statement.getSttText(),
            statement.getSttText() != null,
            statement.getCreatedAt()
        );
    }
}
