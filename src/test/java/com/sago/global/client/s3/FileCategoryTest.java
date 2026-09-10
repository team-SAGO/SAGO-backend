package com.sago.global.client.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 검증 실패 메시지는 400으로 사용자에게 그대로 노출되므로, 문구 자체가 동작의 일부다.
 */
class FileCategoryTest {

    private static final long ONE_MB = 1024 * 1024L;

    @Test
    @DisplayName("용량 초과 메시지는 바이트가 아니라 MB로 안내한다")
    void describesSizeInMegabytes() {
        assertThatThrownBy(() -> FileCategory.PROFILE_IMAGE.validate("jpg", 7 * ONE_MB))
            .isInstanceOf(S3ValidationException.class)
            .hasMessage("파일 용량이 너무 큽니다: 7MB (상한 5MB)");
    }

    @Test
    @DisplayName("상한을 조금만 넘겨도 상한보다 큰 값으로 보인다")
    void roundsUpSoMessageNeverMatchesTheLimit() {
        // 내림하면 "5MB (상한 5MB)"가 되어 왜 거부됐는지 알 수 없다
        assertThatThrownBy(() -> FileCategory.PROFILE_IMAGE.validate("jpg", 5 * ONE_MB + 1))
            .hasMessage("파일 용량이 너무 큽니다: 5.1MB (상한 5MB)");
    }

    @Test
    @DisplayName("허용 확장자는 정렬해서 보여준다")
    void listsAllowedExtensionsInStableOrder() {
        // Set의 기본 문자열은 순서가 정해져 있지 않아 실행마다 달라진다
        assertThatThrownBy(() -> FileCategory.PROFILE_IMAGE.validate("gif", ONE_MB))
            .hasMessage("허용되지 않은 파일 형식입니다: gif (허용: jpeg, jpg, png, webp)");
    }

    @Test
    @DisplayName("확장자가 없으면 null을 노출하지 않고 허용 형식을 안내한다")
    void explainsMissingExtensionWithoutPrintingNull() {
        assertThatThrownBy(() -> FileCategory.PROFILE_IMAGE.validate(null, ONE_MB))
            .isInstanceOf(S3ValidationException.class)
            .hasMessage("파일 확장자를 알 수 없습니다. 허용되는 형식: jpeg, jpg, png, webp");
    }

    @Test
    @DisplayName("빈 파일은 용량 안내가 아니라 별도 메시지로 거부한다")
    void rejectsEmptyFileWithOwnMessage() {
        assertThatThrownBy(() -> FileCategory.PROFILE_IMAGE.validate("jpg", 0))
            .hasMessage("빈 파일은 업로드할 수 없습니다");
    }

    @Test
    @DisplayName("상한과 같은 크기는 통과한다")
    void acceptsFileExactlyAtLimit() {
        assertThatCode(() -> FileCategory.PROFILE_IMAGE.validate("jpg", 5 * ONE_MB))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개수 초과 메시지는 상한과 요청 수를 함께 알려준다")
    void describesCountLimitAndRequested() {
        assertThatThrownBy(() -> FileCategory.ACCIDENT_PHOTO.validateCount(12))
            .isInstanceOf(S3ValidationException.class)
            .hasMessage("한 번에 올릴 수 있는 파일은 10개까지입니다 (요청 12개)");
    }
}
