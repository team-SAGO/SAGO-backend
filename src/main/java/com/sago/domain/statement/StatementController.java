package com.sago.domain.statement;

import com.sago.domain.statement.dto.StatementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 음성 진술 (FR-04).
 *
 * 사고 하위 리소스이므로 경로를 사고 아래에 둔다. 체크리스트와 같은 이유로, 경로에 사고
 * 번호가 있어야 서버가 소유권을 확인할 근거가 요청에 남는다.
 */
@RestController
@RequestMapping("/api/accidents/{accidentId}/statements")
public class StatementController {

    private final StatementRecordService statementRecordService;

    public StatementController(StatementRecordService statementRecordService) {
        this.statementRecordService = statementRecordService;
    }

    /**
     * 음성 진술 등록. 인식에 실패해도 원본 음성은 저장되고 201이 나간다 —
     * 응답의 transcribed로 성공 여부를 구분한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse record(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long accidentId,
                                    @RequestPart("audio") MultipartFile audio) {
        return statementRecordService.record(userId, accidentId, audio);
    }

    @GetMapping
    public List<StatementResponse> getStatements(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long accidentId) {
        return statementRecordService.getStatements(userId, accidentId);
    }
}
