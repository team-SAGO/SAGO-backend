package com.sago.domain.terms;

import com.sago.domain.terms.dto.TermsAgreementItem;
import com.sago.domain.terms.dto.TermsAgreementRequest;
import com.sago.domain.terms.dto.TermsAgreementStatus;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermsServiceTest {

    private TermsAgreementRepository termsAgreementRepository;
    private UserRepository userRepository;
    private TermsService termsService;

    private final User user = User.builder().email("rider@example.com").nickname("라이더").build();
    private final List<TermsAgreement> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        termsAgreementRepository = mock(TermsAgreementRepository.class);
        userRepository = mock(UserRepository.class);
        termsService = new TermsService(catalog("1.0"), termsAgreementRepository, userRepository);

        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(termsAgreementRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<TermsAgreement> saved = invocation.getArgument(0);
            saved.forEach(stored::add);
            return stored;
        });
        when(termsAgreementRepository.findLatestByUserId(anyLong())).thenAnswer(i -> stored);
    }

    @Test
    @DisplayName("필수 약관에 모두 동의하면 저장된다")
    void agreeToAllRequiredTerms() {
        List<TermsAgreementStatus> statuses = termsService.agree(1L, allAgreed());

        assertThat(stored).hasSize(4);
        assertThat(statuses).allSatisfy(s -> assertThat(s.reagreeNeeded()).isFalse());
    }

    @Test
    @DisplayName("선택 약관은 동의하지 않아도 저장된다")
    void optionalTermsMayBeDeclined() {
        termsService.agree(1L, request(true, true, true, false));

        assertThat(stored).hasSize(4);
        assertThat(statusOf(termsService.getStatuses(1L), TermsType.MARKETING).agreed()).isFalse();
    }

    @Test
    @DisplayName("선택 약관을 거부해도 재동의 대상이 아니다")
    void declinedOptionalTermsDoNotRequireReagreement() {
        termsService.agree(1L, request(true, true, true, false));

        assertThat(statusOf(termsService.getStatuses(1L), TermsType.MARKETING).reagreeNeeded())
            .isFalse();
    }

    @Test
    @DisplayName("필수 약관을 거부하면 아무것도 저장되지 않는다")
    void decliningRequiredTermsIsRejected() {
        assertThatThrownBy(() -> termsService.agree(1L, request(true, false, true, true)))
            .isInstanceOf(RequiredTermsNotAgreedException.class);

        verify(termsAgreementRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("필수 약관이 요청에서 빠져도 거부된다")
    void omittedRequiredTermsAreRejected() {
        TermsAgreementRequest partial = new TermsAgreementRequest(List.of(
            new TermsAgreementItem(TermsType.SERVICE, true)));

        assertThatThrownBy(() -> termsService.agree(1L, partial))
            .isInstanceOf(RequiredTermsNotAgreedException.class);
    }

    @Test
    @DisplayName("같은 약관이 중복으로 오면 거부된다")
    void duplicateTermsInRequestAreRejected() {
        TermsAgreementRequest duplicated = new TermsAgreementRequest(List.of(
            new TermsAgreementItem(TermsType.SERVICE, true),
            new TermsAgreementItem(TermsType.SERVICE, false)));

        assertThatThrownBy(() -> termsService.agree(1L, duplicated))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("탈퇴한 회원은 약관에 동의할 수 없다")
    void withdrawnUserCannotAgree() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termsService.agree(1L, allAgreed()))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("동의한 적이 없으면 필수 약관이 재동의 대상으로 나온다")
    void neverAgreedRequiredTermsNeedAgreement() {
        List<TermsAgreementStatus> statuses = termsService.getStatuses(1L);

        assertThat(statusOf(statuses, TermsType.SERVICE).agreedVersion()).isNull();
        assertThat(statusOf(statuses, TermsType.SERVICE).reagreeNeeded()).isTrue();
        assertThat(statusOf(statuses, TermsType.MARKETING).reagreeNeeded()).isFalse();
    }

    @Test
    @DisplayName("약관이 개정되면 이미 동의한 필수 약관도 재동의 대상이 된다")
    void versionBumpRequiresReagreement() {
        termsService.agree(1L, allAgreed());

        // 같은 동의 기록을 버전이 오른 카탈로그로 다시 판단한다.
        TermsService afterRevision =
            new TermsService(catalog("2.0"), termsAgreementRepository, userRepository);
        List<TermsAgreementStatus> statuses = afterRevision.getStatuses(1L);

        TermsAgreementStatus service = statusOf(statuses, TermsType.SERVICE);
        assertThat(service.agreed()).isTrue();
        assertThat(service.agreedVersion()).isEqualTo("1.0");
        assertThat(service.currentVersion()).isEqualTo("2.0");
        assertThat(service.reagreeNeeded()).isTrue();
    }

    @Test
    @DisplayName("동의 이력은 덮어쓰지 않고 쌓인다")
    void agreementsAreAppendedNotOverwritten() {
        termsService.agree(1L, allAgreed());
        termsService.agree(1L, request(true, true, true, false));

        assertThat(stored).hasSize(8);
    }

    private TermsAgreementRequest allAgreed() {
        return request(true, true, true, true);
    }

    private TermsAgreementRequest request(boolean service, boolean privacy,
                                          boolean location, boolean marketing) {
        return new TermsAgreementRequest(List.of(
            new TermsAgreementItem(TermsType.SERVICE, service),
            new TermsAgreementItem(TermsType.PRIVACY, privacy),
            new TermsAgreementItem(TermsType.LOCATION, location),
            new TermsAgreementItem(TermsType.MARKETING, marketing)));
    }

    private TermsAgreementStatus statusOf(List<TermsAgreementStatus> statuses, TermsType type) {
        return statuses.stream()
            .filter(s -> s.type() == type)
            .findFirst()
            .orElseThrow();
    }

    /** terms.yml 대신 테스트에서 직접 구성한 카탈로그. 버전을 바꿔가며 재동의 판단을 확인한다. */
    private TermsCatalog catalog(String version) {
        TermsProperties properties = new TermsProperties();
        properties.setDocuments(List.of(
            document(TermsType.SERVICE, true, version, "서비스 이용약관"),
            document(TermsType.PRIVACY, true, version, "개인정보 수집·이용 동의"),
            document(TermsType.LOCATION, true, version, "위치기반서비스 이용 동의"),
            document(TermsType.MARKETING, false, version, "광고성 정보 수신 동의")));
        return new TermsCatalog(properties);
    }

    private TermsDocument document(TermsType type, boolean required, String version, String title) {
        TermsDocument document = new TermsDocument();
        document.setType(type);
        document.setRequired(required);
        document.setVersion(version);
        document.setTitle(title);
        return document;
    }
}
