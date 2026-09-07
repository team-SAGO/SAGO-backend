package com.sago.domain.terms;

import com.sago.domain.terms.dto.TermsAgreementItem;
import com.sago.domain.terms.dto.TermsAgreementRequest;
import com.sago.domain.terms.dto.TermsAgreementStatus;
import com.sago.domain.terms.dto.TermsDocumentResponse;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이용약관 동의 (FR-01).
 *
 * 약관의 종류·필수 여부·현재 버전은 terms.yml에서 읽고, DB에는 동의 이력만 남긴다.
 */
@Service
public class TermsService {

    private final TermsCatalog catalog;
    private final TermsAgreementRepository termsAgreementRepository;
    private final UserRepository userRepository;

    public TermsService(TermsCatalog catalog,
                        TermsAgreementRepository termsAgreementRepository,
                        UserRepository userRepository) {
        this.catalog = catalog;
        this.termsAgreementRepository = termsAgreementRepository;
        this.userRepository = userRepository;
    }

    /** 동의 화면에 보여줄 약관 목록. 로그인 전에도 볼 수 있어야 하므로 회원과 무관하다. */
    public List<TermsDocumentResponse> getDocuments() {
        return catalog.findAll().stream()
            .map(TermsDocumentResponse::from)
            .toList();
    }

    @Transactional
    public List<TermsAgreementStatus> agree(Long userId, TermsAgreementRequest request) {
        Map<TermsType, Boolean> submitted = toMap(request.agreements());

        List<TermsType> unknown = catalog.unknownTypes(submitted.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("정의되지 않은 약관 유형입니다: " + unknown);
        }

        verifyRequiredAgreed(submitted);

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));

        Map<TermsType, TermsAgreement> latest = findLatestByType(userId);

        // 기존 행을 고치지 않고 새 행을 쌓는다. 과거 동의 기록이 남아야 하기 때문이다.
        // 다만 상태가 그대로인 약관은 새로 남기지 않는다 — 아래 hasChanged() 참고.
        List<TermsAgreement> newRows = submitted.entrySet().stream()
            .filter(entry -> hasChanged(latest.get(entry.getKey()),
                entry.getValue(), catalog.find(entry.getKey()).getVersion()))
            .map(entry -> TermsAgreement.builder()
                .user(user)
                .termsType(entry.getKey())
                .agreed(entry.getValue())
                .version(catalog.find(entry.getKey()).getVersion())
                .build())
            .toList();
        termsAgreementRepository.saveAll(newRows);

        // 같은 빈 안의 호출이라 프록시를 타지 않는다. getStatuses()의 readOnly 트랜잭션이
        // 새로 열리는 게 아니라 이 쓰기 트랜잭션 안에서 실행된다. 방금 저장한 행은 조회 직전
        // auto flush로 반영되므로 결과는 맞다. 이 메서드를 다른 빈으로 옮기거나 flush 정책을
        // 바꿀 때 이 전제가 깨질 수 있어 적어둔다.
        return getStatuses(userId);
    }

    /**
     * 새 동의 기록을 남겨야 하는지 판단한다.
     *
     * 동의한 적이 없거나, 동의 여부가 바뀌었거나, 약관이 개정되어 버전이 달라진 경우에만 남긴다.
     *
     * 상태가 그대로인데도 행을 쌓으면 실제로는 일어나지 않은 동의를 기록하게 된다.
     * 설정 화면에서 마케팅 수신만 껐다 켜는 경우, 사용자에게 이용약관을 다시 보여준 것이
     * 아닌데도 그 시각에 이용약관에 동의한 기록이 남는다. 동의 이력은 나중에 근거로 쓰이는
     * 자료라, 없었던 동의가 섞이면 기록 전체의 신뢰가 떨어진다.
     */
    private boolean hasChanged(TermsAgreement latest, boolean agreed, String currentVersion) {
        return latest == null
            || latest.isAgreed() != agreed
            || !currentVersion.equals(latest.getVersion());
    }

    private Map<TermsType, TermsAgreement> findLatestByType(Long userId) {
        return termsAgreementRepository.findLatestByUserId(userId).stream()
            .collect(Collectors.toMap(
                TermsAgreement::getTermsType,
                agreement -> agreement,
                (first, second) -> second,
                () -> new EnumMap<>(TermsType.class)));
    }

    @Transactional(readOnly = true)
    public List<TermsAgreementStatus> getStatuses(Long userId) {
        Map<TermsType, TermsAgreement> latest = findLatestByType(userId);

        return catalog.findAll().stream()
            .map(document -> toStatus(document, latest.get(document.getType())))
            .toList();
    }

    private TermsAgreementStatus toStatus(TermsDocument document, TermsAgreement agreement) {
        boolean agreed = agreement != null && agreement.isAgreed();
        String agreedVersion = agreement == null ? null : agreement.getVersion();

        /*
         * 재동의가 필요한 경우는 두 가지다.
         * - 필수인데 아직 동의하지 않았다
         * - 동의는 했는데 그 뒤로 약관이 개정되어 버전이 달라졌다
         * 선택 약관은 동의하지 않은 상태가 정상이므로 재동의 대상이 아니다.
         */
        boolean reagreeNeeded = document.isRequired()
            && (!agreed || !document.getVersion().equals(agreedVersion));

        return new TermsAgreementStatus(
            document.getType(),
            document.isRequired(),
            document.getTitle(),
            agreed,
            agreedVersion,
            document.getVersion(),
            reagreeNeeded
        );
    }

    /**
     * 요청에 담긴 동의 내역을 유형별로 정리한다.
     * 같은 유형이 두 번 들어오면 판단이 갈리므로 거부한다 — 뒤엣것으로 덮으면
     * 클라이언트 실수가 조용히 넘어가고, 그 값이 그대로 동의 근거로 남는다.
     */
    private Map<TermsType, Boolean> toMap(List<TermsAgreementItem> items) {
        Map<TermsType, Boolean> map = new EnumMap<>(TermsType.class);
        for (TermsAgreementItem item : items) {
            if (map.put(item.type(), item.agreed()) != null) {
                throw new IllegalArgumentException("같은 약관이 중복으로 전달되었습니다: " + item.type());
            }
        }
        return map;
    }

    /**
     * 필수 약관이 모두 동의되었는지 확인한다.
     * 요청에서 아예 빠진 필수 약관도 미동의로 본다 — 빠뜨린 것과 거부한 것을 구분해봐야
     * 어느 쪽이든 서비스를 이용할 수 없기 때문이다.
     */
    private void verifyRequiredAgreed(Map<TermsType, Boolean> submitted) {
        Set<TermsType> notAgreed = catalog.requiredTypes().stream()
            .filter(type -> !Boolean.TRUE.equals(submitted.get(type)))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(TermsType.class)));

        if (!notAgreed.isEmpty()) {
            throw new RequiredTermsNotAgreedException(
                "필수 약관에 동의해야 서비스를 이용할 수 있습니다: " + notAgreed);
        }
    }
}
