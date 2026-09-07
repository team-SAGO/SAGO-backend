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

        // 기존 행을 고치지 않고 새 행을 쌓는다. 과거 동의 기록이 남아야 하기 때문이다.
        List<TermsAgreement> saved = submitted.entrySet().stream()
            .map(entry -> TermsAgreement.builder()
                .user(user)
                .termsType(entry.getKey())
                .agreed(entry.getValue())
                .version(catalog.find(entry.getKey()).getVersion())
                .build())
            .toList();
        termsAgreementRepository.saveAll(saved);

        return getStatuses(userId);
    }

    @Transactional(readOnly = true)
    public List<TermsAgreementStatus> getStatuses(Long userId) {
        Map<TermsType, TermsAgreement> latest = termsAgreementRepository.findLatestByUserId(userId)
            .stream()
            .collect(Collectors.toMap(
                TermsAgreement::getTermsType,
                agreement -> agreement,
                (first, second) -> second,
                () -> new EnumMap<>(TermsType.class)));

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
            .collect(Collectors.toCollection(() -> java.util.EnumSet.noneOf(TermsType.class)));

        if (!notAgreed.isEmpty()) {
            throw new RequiredTermsNotAgreedException(
                "필수 약관에 동의해야 서비스를 이용할 수 있습니다: " + notAgreed);
        }
    }
}
