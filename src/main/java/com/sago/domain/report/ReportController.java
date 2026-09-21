package com.sago.domain.report;

import com.sago.domain.report.ReportService.ReportWriting;
import com.sago.domain.report.dto.ReportResponse;
import com.sago.domain.report.dto.ReportUpdateRequest;
import com.sago.domain.report.dto.ReportWriteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사고 경위서 (Step 9). 사고 하위 리소스이고, 사고당 한 건이라 경로에 경위서 번호를 두지 않는다.
 */
@RestController
@RequestMapping("/api/accidents/{accidentId}/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 경위서 작성.
     *
     * 본문 없이 부르면 AI가 사고 기록·진술·사진 태그로 초안을 만들고, 본문을 담아 보내면 그대로 저장한다.
     * 처음 만들면 201, 이미 있던 경위서를 갈아끼우면 200이다(version이 올라간다).
     *
     * AI 생성에 실패하면 502가 나간다. 그때는 사용자가 직접 쓴 본문을 담아 같은 경로로 다시 보내면 된다.
     */
    @PostMapping
    public ResponseEntity<ReportResponse> write(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long accidentId,
                                                @Valid @RequestBody(required = false)
                                                ReportWriteRequest request) {
        ReportWriting result = reportService.write(userId, accidentId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(result.report());
    }

    @GetMapping
    public ReportResponse getReport(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long accidentId) {
        return reportService.getReport(userId, accidentId);
    }

    /** 확정 전 본문 수정. 확정된 경위서는 409다. */
    @PatchMapping
    public ReportResponse updateNarrative(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long accidentId,
                                          @Valid @RequestBody ReportUpdateRequest request) {
        return reportService.updateNarrative(userId, accidentId, request);
    }

    /**
     * 경위서 확정. 사고 처리도 함께 끝난다.
     *
     * 확정 후에는 수정·재생성이 막히고, 다음 사고 발생 버튼은 새 사고를 만든다.
     */
    @PostMapping("/confirm")
    public ReportResponse confirm(@AuthenticationPrincipal Long userId,
                                  @PathVariable Long accidentId) {
        return reportService.confirm(userId, accidentId);
    }
}
