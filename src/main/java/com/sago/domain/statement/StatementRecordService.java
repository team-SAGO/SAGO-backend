package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.statement.dto.StatementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Step 4 — 음성 진술 저장과 조회 (FR-04).
 *
 * 업로드는 {@link StatementUploadService}, 음성 인식은 {@link StatementService}가 맡고,
 * 여기서는 누가 어느 사고에 진술을 남길 수 있는지를 정한다.
 */
@Service
public class StatementRecordService {

    private final AccidentService accidentService;
    private final StatementUploadService statementUploadService;
    private final StatementRepository statementRepository;

    public StatementRecordService(AccidentService accidentService,
                                  StatementUploadService statementUploadService,
                                  StatementRepository statementRepository) {
        this.accidentService = accidentService;
        this.statementUploadService = statementUploadService;
        this.statementRepository = statementRepository;
    }

    /**
     * 음성을 올리고 인식해 진술로 저장한다.
     *
     * 트랜잭션을 걸지 않는다. S3 업로드와 STT 호출이 모두 이 흐름 안에 있어서, 감싸버리면
     * 두 외부 호출이 끝날 때까지 DB 커넥션을 붙잡는다.
     *
     * getOwnedAccident가 돌려준 사고는 트랜잭션 밖에서 쓰게 되는데, 이후에는 진술의 외래 키로만
     * 넘겨지고 지연 로딩 필드는 건드리지 않아 안전하다.
     */
    public StatementResponse record(Long userId, Long accidentId, MultipartFile audioFile) {
        Accident accident = accidentService.getOwnedAccident(userId, accidentId);
        return StatementResponse.from(statementUploadService.upload(accident, audioFile));
    }

    /** 사고의 진술 목록. 먼저 남긴 진술이 앞에 온다. */
    @Transactional(readOnly = true)
    public List<StatementResponse> getStatements(Long userId, Long accidentId) {
        accidentService.getOwnedAccident(userId, accidentId);

        return statementRepository.findByAccident_AccidentIdOrderByCreatedAtAsc(accidentId).stream()
            .map(StatementResponse::from)
            .toList();
    }
}
