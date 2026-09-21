package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.report.ReportStore.ReportInputs;
import com.sago.domain.report.dto.ReportResponse;
import com.sago.domain.report.dto.ReportUpdateRequest;
import com.sago.domain.report.dto.ReportWriteRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Step 9 — 사고 경위서 저장·조회·확정 (#86).
 *
 * 생성 자체는 {@link ReportGenerationService}가, 저장은 {@link ReportStore}가 맡고, 여기서는
 * 누가 어느 사고의 경위서를 다룰 수 있는지와 "AI로 만들지, 사용자가 직접 쓴 것을 저장할지"를 정한다.
 *
 * 쓰기 메서드에는 트랜잭션을 걸지 않는다. Gemini 호출이 이 흐름 안에 있어서, 감싸면 응답이 올 때까지
 * DB 커넥션을 붙잡는다.
 */
@Service
public class ReportService {

    /** 사용자가 직접 쓴 경위서에 붙는 문구. AI가 정리한 것이 아니라는 사실이 서류에 남아야 한다. */
    private static final String USER_WRITTEN_DISCLAIMER = "본 문서는 사용자가 직접 작성한 자료입니다.";

    private final AccidentService accidentService;
    private final ReportGenerationService reportGenerationService;
    private final ReportStore reportStore;

    public ReportService(AccidentService accidentService,
                         ReportGenerationService reportGenerationService,
                         ReportStore reportStore) {
        this.accidentService = accidentService;
        this.reportGenerationService = reportGenerationService;
        this.reportStore = reportStore;
    }

    /**
     * 경위서를 만들어 저장한다. 이미 있으면 내용을 갈아끼운다.
     *
     * 본문을 담아 보내면 AI를 부르지 않고 그대로 저장한다. AI 생성이 실패했을 때 사용자가 직접
     * 쓰는 경로이고, 실패하지 않았더라도 사용자가 처음부터 직접 쓰겠다고 하면 막을 이유가 없다.
     */
    public ReportWriting write(Long userId, Long accidentId, ReportWriteRequest request) {
        Accident accident = accidentService.getOwnedAccident(userId, accidentId);
        boolean first = reportStore.find(accidentId).isEmpty();

        Report draft = request != null && request.writtenByUser()
            ? userWritten(accident, request.narrative())
            : generate(accident, accidentId);

        return new ReportWriting(reportStore.saveOrReplace(accidentId, draft), first);
    }

    /** 사고의 경위서. 아직 만들지 않았으면 404다. */
    public ReportResponse getReport(Long userId, Long accidentId) {
        accidentService.getOwnedAccident(userId, accidentId);

        return reportStore.find(accidentId)
            .orElseThrow(() -> new ReportNotFoundException("경위서가 아직 없습니다."));
    }

    /** 확정 전 본문 수정. */
    public ReportResponse updateNarrative(Long userId, Long accidentId, ReportUpdateRequest request) {
        accidentService.getOwnedAccident(userId, accidentId);

        return reportStore.updateNarrative(accidentId, request.narrative());
    }

    /** 확정. 이후 내용은 바꿀 수 없고, 사고도 함께 끝난다. */
    public ReportResponse confirm(Long userId, Long accidentId) {
        accidentService.getOwnedAccident(userId, accidentId);

        return reportStore.confirm(accidentId);
    }

    /**
     * AI 초안을 만든다.
     *
     * 생성에 실패하면(Gemini 오류·응답 형식 오류) 빈 Optional이 돌아온다. 경위서에는 정적 폴백이
     * 없어서 — 진술 내용을 서버가 지어낼 수는 없다 — 사용자에게 알리고 직접 작성으로 넘긴다.
     */
    private Report generate(Accident accident, Long accidentId) {
        ReportInputs inputs = reportStore.collectInputs(accidentId);

        return reportGenerationService
            .generateReport(accident, inputs.statementText(), inputs.questions(), inputs.photoTags())
            .orElseThrow(() -> new ReportGenerationFailedException(
                "경위서 생성에 실패했습니다. 잠시 후 다시 시도하거나 직접 작성해주세요."));
    }

    /**
     * 사용자가 직접 쓴 경위서.
     *
     * 요약·미확인 항목은 비워 둔다. AI가 본문에서 뽑아내는 값이라 서버가 대신 만들 수 없고,
     * 빈 값이 "아직 정리되지 않았다"는 사실을 그대로 드러낸다.
     */
    private Report userWritten(Accident accident, String narrative) {
        return Report.builder()
            .accident(accident)
            .narrative(narrative)
            .summary(List.of())
            .unverifiedItems(List.of())
            .disclaimer(USER_WRITTEN_DISCLAIMER)
            .build();
    }

    /**
     * @param report  저장된 경위서
     * @param created 이번 요청에서 처음 만들어졌으면 true, 기존 경위서를 갈아끼웠으면 false
     */
    public record ReportWriting(ReportResponse report, boolean created) {
    }
}
