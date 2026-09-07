package com.sago.domain.terms;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * terms.yml에 정의된 약관 목록을 읽어 들이고 검증한다.
 *
 * 기동 시점에 한 번 검증하는 이유는, yml 키를 잘못 적으면 값이 null인 채로 바인딩되기 때문이다.
 * 그대로 두면 버전 없는 약관에 사용자가 동의하는 상황이 생기고, 동의 기록이 근거로 쓸 수 없게 된다.
 */
@Component
public class TermsCatalog {

    private final Map<TermsType, TermsDocument> documents = new EnumMap<>(TermsType.class);

    public TermsCatalog(TermsProperties properties) {
        if (properties.getDocuments().isEmpty()) {
            throw new IllegalStateException("약관 목록이 비어 있습니다. terms.yml을 확인하세요.");
        }

        for (TermsDocument document : properties.getDocuments()) {
            validate(document);
            if (documents.put(document.getType(), document) != null) {
                throw new IllegalStateException(
                    "약관 유형 " + document.getType() + "이(가) terms.yml에 중복 정의되었습니다.");
            }
        }
    }

    /** 동의 화면에 보여줄 전체 약관. 정의된 순서를 그대로 유지한다. */
    public Collection<TermsDocument> findAll() {
        return documents.values();
    }

    public TermsDocument find(TermsType type) {
        TermsDocument document = documents.get(type);
        if (document == null) {
            throw new IllegalArgumentException("정의되지 않은 약관 유형입니다: " + type);
        }
        return document;
    }

    public Set<TermsType> requiredTypes() {
        return documents.values().stream()
            .filter(TermsDocument::isRequired)
            .map(TermsDocument::getType)
            .collect(Collectors.toCollection(() -> java.util.EnumSet.noneOf(TermsType.class)));
    }

    /** 정의된 모든 약관 유형. 요청에 빠진 항목이 있는지 확인하는 데 쓴다. */
    public Set<TermsType> allTypes() {
        return documents.keySet();
    }

    private static void validate(TermsDocument document) {
        if (document.getType() == null) {
            throw new IllegalStateException("약관의 type이 비어 있습니다. terms.yml을 확인하세요.");
        }
        if (isBlank(document.getVersion())) {
            throw new IllegalStateException(
                "약관 " + document.getType() + "의 version이 비어 있습니다. terms.yml을 확인하세요.");
        }
        if (isBlank(document.getTitle())) {
            throw new IllegalStateException(
                "약관 " + document.getType() + "의 title이 비어 있습니다. terms.yml을 확인하세요.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 정의되지 않은 유형이 요청에 들어왔는지 확인한다. */
    public List<TermsType> unknownTypes(Collection<TermsType> types) {
        return types.stream()
            .filter(type -> !documents.containsKey(type))
            .toList();
    }
}
