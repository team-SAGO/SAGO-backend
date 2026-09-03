package com.sago.domain.photo;

import java.util.List;

/**
 * Vision 태깅 결과. additionalConfirmationItems는 값이 있으면
 * SupplementQuestionService의 ADDITIONAL 회차 추가 컨텍스트로 그대로 넘기면 된다.
 */
public record PhotoTaggingResult(List<PhotoTag> tags, List<String> additionalConfirmationItems) {

    public static PhotoTaggingResult empty() {
        return new PhotoTaggingResult(List.of(), List.of());
    }
}
