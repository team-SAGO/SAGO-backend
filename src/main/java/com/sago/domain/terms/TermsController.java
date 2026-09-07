package com.sago.domain.terms;

import com.sago.domain.terms.dto.TermsAgreementRequest;
import com.sago.domain.terms.dto.TermsAgreementStatus;
import com.sago.domain.terms.dto.TermsDocumentResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 이용약관 화면 (FR-01).
 *
 * 최초 로그인 응답의 newUser가 true면 클라이언트가 이 화면으로 진입한다.
 * 설정 화면에서 마케팅 수신 동의를 바꾸거나 약관 개정 후 재동의를 받을 때도 같은 API를 쓴다.
 */
@RestController
@RequestMapping("/api/terms")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    /**
     * 동의받을 약관 목록.
     *
     * 로그인하지 않아도 볼 수 있도록 열어둔다. 가입 전에 약관을 확인하는 것은 당연한 흐름이고,
     * 회원마다 달라지지 않는 정적 정보라 감출 이유가 없다. (SecurityConfig에 공개 경로로 등록)
     */
    @GetMapping
    public List<TermsDocumentResponse> getDocuments() {
        return termsService.getDocuments();
    }

    /** 동의 내역 저장. 필수 약관에 하나라도 동의하지 않으면 거부된다. */
    @PostMapping("/agreements")
    public List<TermsAgreementStatus> agree(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody TermsAgreementRequest request) {
        return termsService.agree(userId, request);
    }

    /** 내 동의 현황. 약관이 개정되었다면 reagreeNeeded가 true로 내려간다. */
    @GetMapping("/agreements")
    public List<TermsAgreementStatus> getMyAgreements(@AuthenticationPrincipal Long userId) {
        return termsService.getStatuses(userId);
    }
}
