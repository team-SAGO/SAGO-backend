package com.sago.domain.terms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * terms.yml을 잘못 적었을 때 기동 시점에 막히는지 확인한다.
 *
 * 이 검증이 없으면 버전이나 제목이 비어 있는 약관에 사용자가 동의하게 되고,
 * 그 동의 기록은 나중에 근거로 쓸 수 없다.
 */
class TermsCatalogTest {

    @Test
    @DisplayName("약관 목록이 비어 있으면 기동에 실패한다")
    void emptyCatalogIsRejected() {
        assertThatThrownBy(() -> new TermsCatalog(new TermsProperties()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("terms.yml");
    }

    @Test
    @DisplayName("버전이 비어 있으면 기동에 실패한다")
    void blankVersionIsRejected() {
        TermsProperties properties = properties(document(TermsType.SERVICE, "", "서비스 이용약관"));

        assertThatThrownBy(() -> new TermsCatalog(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("version");
    }

    @Test
    @DisplayName("제목이 비어 있으면 기동에 실패한다")
    void blankTitleIsRejected() {
        TermsProperties properties = properties(document(TermsType.SERVICE, "1.0", ""));

        assertThatThrownBy(() -> new TermsCatalog(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("title");
    }

    @Test
    @DisplayName("같은 약관 유형이 두 번 정의되면 기동에 실패한다")
    void duplicateTypeIsRejected() {
        TermsProperties properties = properties(
            document(TermsType.SERVICE, "1.0", "서비스 이용약관"),
            document(TermsType.SERVICE, "2.0", "서비스 이용약관 개정"));

        assertThatThrownBy(() -> new TermsCatalog(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("중복");
    }

    private TermsProperties properties(TermsDocument... documents) {
        TermsProperties properties = new TermsProperties();
        properties.setDocuments(List.of(documents));
        return properties;
    }

    private TermsDocument document(TermsType type, String version, String title) {
        TermsDocument document = new TermsDocument();
        document.setType(type);
        document.setRequired(true);
        document.setVersion(version);
        document.setTitle(title);
        return document;
    }
}
