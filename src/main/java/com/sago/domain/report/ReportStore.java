package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentNotFoundException;
import com.sago.domain.accident.AccidentRepository;
import com.sago.domain.photo.Photo;
import com.sago.domain.photo.PhotoRepository;
import com.sago.domain.photo.PhotoTag;
import com.sago.domain.photo.PhotoTagRepository;
import com.sago.domain.report.dto.ReportResponse;
import com.sago.domain.statement.Statement;
import com.sago.domain.statement.StatementRepository;
import com.sago.domain.supplementquestion.SupplementQuestion;
import com.sago.domain.supplementquestion.SupplementQuestionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 경위서의 DB 작업만 담당한다.
 *
 * {@link ReportService}에서 분리한 이유는 Gemini 호출을 트랜잭션 밖에 두기 위해서다. 경위서는
 * 프롬프트가 길어 응답이 30초까지 걸릴 수 있는데, 그동안 DB 커넥션을 붙잡으면 안 된다
 * (체크리스트의 ChecklistStore와 같은 구조).
 *
 * 모든 메서드가 엔티티가 아니라 응답 DTO를 돌려준다. 요약·미확인 항목이 지연 로딩이라
 * 트랜잭션 밖에서 변환하면 LazyInitializationException이 나기 때문이다.
 */
@Component
public class ReportStore {

    private final ReportRepository reportRepository;
    private final AccidentRepository accidentRepository;
    private final StatementRepository statementRepository;
    private final SupplementQuestionRepository supplementQuestionRepository;
    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;

    public ReportStore(ReportRepository reportRepository, AccidentRepository accidentRepository,
                       StatementRepository statementRepository,
                       SupplementQuestionRepository supplementQuestionRepository,
                       PhotoRepository photoRepository, PhotoTagRepository photoTagRepository) {
        this.reportRepository = reportRepository;
        this.accidentRepository = accidentRepository;
        this.statementRepository = statementRepository;
        this.supplementQuestionRepository = supplementQuestionRepository;
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ReportResponse> find(Long accidentId) {
        return reportRepository.findByAccident_AccidentId(accidentId).map(ReportResponse::from);
    }

    /**
     * 경위서 생성에 넣을 자료를 모은다.
     *
     * 진술이 여러 건이면 시간 순으로 이어 붙인다. 사고 직후 진술은 짧게 나눠 녹음될 수 있어(#69),
     * 한 건만 쓰면 뒤쪽 내용이 통째로 빠진다. 인식에 실패한 진술은 텍스트가 없어 건너뛴다.
     */
    @Transactional(readOnly = true)
    public ReportInputs collectInputs(Long accidentId) {
        String statementText = statementRepository
            .findByAccident_AccidentIdOrderByCreatedAtAsc(accidentId).stream()
            .map(Statement::getSttText)
            .filter(text -> text != null && !text.isBlank())
            .reduce((previous, next) -> previous + System.lineSeparator() + next)
            .orElse("");

        List<SupplementQuestion> questions =
            supplementQuestionRepository.findByAccident_AccidentIdOrderByCreatedAtAsc(accidentId);

        // 사고당 사진은 최대 10장이라 사진마다 태그를 읽어도 부담이 없다.
        List<PhotoTag> photoTags = new ArrayList<>();
        for (Photo photo : photoRepository.findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(accidentId)) {
            photoTags.addAll(photoTagRepository.findByPhoto_PhotoId(photo.getPhotoId()));
        }

        return new ReportInputs(statementText, questions, photoTags);
    }

    /**
     * 경위서를 저장한다. 이미 있으면 내용을 갈아끼우고 version을 올린다.
     *
     * 사고 행을 잠근다. 생성 요청이 거의 동시에 두 번 오면 둘 다 "경위서 없음"을 보고 각자 저장을
     * 시도해, 유니크 제약에 걸린 쪽이 오류로 끝난다. 잠가두면 두 번째 요청은 재생성 경로를 탄다.
     */
    @Transactional
    public ReportResponse saveOrReplace(Long accidentId, Report draft) {
        Accident accident = lockAccident(accidentId);

        Optional<Report> existing = reportRepository.findByAccident_AccidentId(accidentId);
        if (existing.isPresent()) {
            Report report = existing.get();
            requireEditable(report);
            report.replaceContent(draft.getNarrative(), draft.getSummary(),
                draft.getUnverifiedItems(), draft.getDisclaimer());
            return ReportResponse.from(report);
        }

        Report saved = reportRepository.save(Report.builder()
            .accident(accident)
            .narrative(draft.getNarrative())
            .summary(draft.getSummary())
            .unverifiedItems(draft.getUnverifiedItems())
            .disclaimer(draft.getDisclaimer())
            .build());

        return ReportResponse.from(saved);
    }

    @Transactional
    public ReportResponse updateNarrative(Long accidentId, String narrative) {
        Report report = reportRepository.findByAccident_AccidentId(accidentId)
            .orElseThrow(() -> new ReportNotFoundException("경위서가 아직 없습니다."));
        requireEditable(report);
        report.updateNarrative(narrative);

        return ReportResponse.from(report);
    }

    /**
     * 경위서를 확정하고 사고를 끝낸다 (#34).
     *
     * 경위서가 사고 대응 플로우의 마지막 산출물이라, 확정이 곧 그 사고의 처리 종료다. 끝난 사고는
     * 이어 쓰기 대상에서 빠져 다음 사고 버튼이 새 사고를 만든다.
     *
     * 이미 확정된 경위서를 다시 확정해도 성공으로 본다 — 결과가 같고, 확정 버튼을 두 번 눌렀다고
     * 오류를 낼 이유가 없다.
     */
    @Transactional
    public ReportResponse confirm(Long accidentId) {
        Accident accident = lockAccident(accidentId);
        Report report = reportRepository.findByAccident_AccidentId(accidentId)
            .orElseThrow(() -> new ReportNotFoundException("경위서가 아직 없습니다."));

        if (!report.isConfirmed()) {
            report.confirm();
        }
        accident.complete();

        return ReportResponse.from(report);
    }

    private void requireEditable(Report report) {
        if (report.isConfirmed()) {
            throw new ReportAlreadyConfirmedException("확정된 경위서는 수정할 수 없습니다.");
        }
    }

    private Accident lockAccident(Long accidentId) {
        return accidentRepository.findByIdForUpdate(accidentId)
            .orElseThrow(() -> new AccidentNotFoundException("사고 기록을 찾을 수 없습니다."));
    }

    /**
     * 경위서 생성에 넣을 자료.
     *
     * @param statementText 진술 텍스트를 시간 순으로 이어 붙인 것. 없으면 빈 문자열이다.
     * @param questions     보완 질문과 답변
     * @param photoTags     사고 사진에서 인식된 태그
     */
    public record ReportInputs(String statementText, List<SupplementQuestion> questions,
                               List<PhotoTag> photoTags) {
    }
}
