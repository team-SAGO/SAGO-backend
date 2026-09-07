package com.sago.domain.terms.dto;

import com.sago.domain.terms.TermsDocument;
import com.sago.domain.terms.TermsType;

/**
 * 동의 화면에 보여줄 약관 하나.
 *
 * @param contentUrl 약관 전문 링크. 아직 게시되지 않았다면 null이다.
 */
public record TermsDocumentResponse(
    TermsType type,
    boolean required,
    String version,
    String title,
    String contentUrl
) {

    public static TermsDocumentResponse from(TermsDocument document) {
        String url = document.getContentUrl();
        return new TermsDocumentResponse(
            document.getType(),
            document.isRequired(),
            document.getVersion(),
            document.getTitle(),
            (url == null || url.isBlank()) ? null : url
        );
    }
}
