package com.sago.global.client.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 다중 업로드의 롤백은 정상 경로에서 실행되지 않아, 깨져도 한참 뒤에나 드러난다.
 * 실패 경로를 목으로 강제해 검증한다.
 */
class S3UploaderTest {

    private S3Client s3Client;
    private S3Uploader s3Uploader;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);

        S3Properties properties = new S3Properties();
        properties.setBucket("sago-test");
        properties.setRegion("ap-northeast-2");

        s3Uploader = new S3Uploader(s3Client, properties);
    }

    private MultipartFile photo(String filename) {
        return new MockMultipartFile("files", filename, "image/jpeg", new byte[] {1, 2, 3});
    }

    private List<MultipartFile> photos(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> photo("photo" + i + ".jpg"))
            .map(MultipartFile.class::cast)
            .toList();
    }

    @Test
    @DisplayName("업로드 도중 실패하면 이미 올라간 파일을 지우고 원래 예외를 전파한다")
    void rollsBackUploadedFilesWhenUploadFails() {
        S3Exception failure = (S3Exception) S3Exception.builder().message("업로드 실패").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build())
            .thenThrow(failure);

        assertThatThrownBy(() -> s3Uploader.uploadAll(photos(3), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3CommunicationException.class)
            .hasCause(failure);

        // 성공한 1장 + putObject가 터진 2장째(저장 후 응답 실패 가능성)까지 정리
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("롤백 삭제가 실패해도 원래 예외를 그대로 전파한다")
    void keepsOriginalExceptionWhenRollbackDeleteFails() {
        S3Exception uploadFailure = (S3Exception) S3Exception.builder().message("업로드 실패").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build())
            .thenThrow(uploadFailure);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(S3Exception.builder().message("삭제 실패").build());

        assertThatThrownBy(() -> s3Uploader.uploadAll(photos(2), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3CommunicationException.class)
            .hasCause(uploadFailure);
    }

    @Test
    @DisplayName("허용되지 않은 확장자가 섞여 있으면 한 장도 올리지 않는다")
    void uploadsNothingWhenAnyFileHasDisallowedExtension() {
        List<MultipartFile> files = List.of(
            photo("photo0.jpg"),
            photo("photo1.jpg"),
            photo("animation.gif"));

        assertThatThrownBy(() -> s3Uploader.uploadAll(files, FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3ValidationException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("개수 상한을 넘으면 한 장도 올리지 않는다")
    void uploadsNothingWhenCountExceedsMax() {
        int overLimit = FileCategory.ACCIDENT_PHOTO.getMaxCount() + 1;

        assertThatThrownBy(() -> s3Uploader.uploadAll(photos(overLimit), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3ValidationException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("개수 상한과 같으면 전부 업로드한다")
    void uploadsAllWhenCountEqualsMax() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());
        int maxCount = FileCategory.ACCIDENT_PHOTO.getMaxCount();

        List<String> urls = s3Uploader.uploadAll(photos(maxCount), FileCategory.ACCIDENT_PHOTO);

        assertThat(urls).hasSize(maxCount);
        verify(s3Client, times(maxCount)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("putObject가 터지면 단건 업로드도 해당 객체를 정리한다")
    void cleansUpWhenSingleUploadFails() {
        S3Exception failure = (S3Exception) S3Exception.builder().message("응답 처리 실패").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(failure);

        assertThatThrownBy(() -> s3Uploader.upload(photo("photo.jpg"), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3CommunicationException.class)
            .hasCause(failure);

        // 객체가 저장된 뒤 응답에서 터졌을 수 있으므로, 호출자가 URL을 못 받는 이 경로에서 직접 정리한다
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(captor.capture());
        assertThat(captor.getValue().key())
            .startsWith(FileCategory.ACCIDENT_PHOTO.getDirectory() + "/");
    }

    @Test
    @DisplayName("정리 삭제가 실패해도 원래 업로드 예외를 전파한다")
    void keepsUploadExceptionWhenCleanupDeleteFails() {
        S3Exception uploadFailure = (S3Exception) S3Exception.builder().message("업로드 실패").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(uploadFailure);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(S3Exception.builder().message("삭제 실패").build());

        assertThatThrownBy(() -> s3Uploader.upload(photo("photo.jpg"), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3CommunicationException.class)
            .hasCause(uploadFailure);
    }

    @Test
    @DisplayName("빈 목록은 업로드를 시도하지 않는다")
    void rejectsEmptyFileList() {
        assertThatThrownBy(() -> s3Uploader.uploadAll(List.of(), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3ValidationException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("일부가 실패해도 성공한 사진은 남기고 실패 목록을 함께 돌려준다")
    void keepsSucceededPhotosWhenSomeFail() {
        // 사고 현장은 재촬영이 불가능한 경우가 많아, 되돌리면 복구할 수 없는 손실이 된다
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build())
            .thenThrow(S3Exception.builder().message("업로드 실패").build())
            .thenReturn(PutObjectResponse.builder().build());

        MultiUploadResult result =
            s3Uploader.uploadAllowingPartial(photos(3), FileCategory.ACCIDENT_PHOTO);

        assertThat(result.uploadedUrls()).hasSize(2);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.index()).isEqualTo(1);
            assertThat(failure.filename()).isEqualTo("photo1.jpg");
            assertThat(failure.type()).isEqualTo(MultiUploadResult.FailureType.STORAGE_ERROR);
        });
    }

    @Test
    @DisplayName("통신 실패 사유에는 S3 오브젝트 키가 담기지 않는다")
    void doesNotLeakObjectKeyInFailureReason() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("업로드 실패").build());

        MultiUploadResult result =
            s3Uploader.uploadAllowingPartial(photos(1), FileCategory.ACCIDENT_PHOTO);

        assertThat(result.failures()).singleElement().satisfies(failure ->
            assertThat(failure.message())
                .doesNotContain(FileCategory.ACCIDENT_PHOTO.getDirectory())
                .doesNotContain("sago-test"));
    }

    @Test
    @DisplayName("검증 실패는 사유를 그대로 알려주고 나머지는 계속 올린다")
    void reportsValidationReasonAndContinues() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());
        List<MultipartFile> files = List.of(photo("ok.jpg"), photo("animation.gif"));

        MultiUploadResult result =
            s3Uploader.uploadAllowingPartial(files, FileCategory.ACCIDENT_PHOTO);

        assertThat(result.uploadedUrls()).hasSize(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.type()).isEqualTo(MultiUploadResult.FailureType.INVALID_FILE);
            assertThat(failure.message()).contains("허용되지 않은 파일 형식입니다");
        });
    }

    @Test
    @DisplayName("파일명에 넣은 태그는 실패 목록에 그대로 실리지 않는다")
    void sanitizesFilenameInFailureList() {
        List<MultipartFile> files = List.of(
            new MockMultipartFile("files", "<img src=x>.gif", "image/gif", new byte[] {1}));

        MultiUploadResult result =
            s3Uploader.uploadAllowingPartial(files, FileCategory.ACCIDENT_PHOTO);

        assertThat(result.failures()).singleElement().satisfies(failure ->
            assertThat(failure.filename()).doesNotContain("<").doesNotContain(">"));
    }

    @Test
    @DisplayName("부분 성공을 허용하지 않는 종류에는 쓸 수 없다")
    void rejectsCategoriesThatDoNotAllowPartialSuccess() {
        // 문서는 다시 찍을 수 있고 세트로 의미가 있어 all-or-nothing을 유지한다
        assertThatThrownBy(() -> s3Uploader.uploadAllowingPartial(photos(1), FileCategory.DOCUMENT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("개수 상한을 넘으면 부분 성공 경로에서도 한 장도 올리지 않는다")
    void stillRejectsOverCountInPartialMode() {
        int overLimit = FileCategory.ACCIDENT_PHOTO.getMaxCount() + 1;

        assertThatThrownBy(() ->
            s3Uploader.uploadAllowingPartial(photos(overLimit), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3ValidationException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("검증 실패와 통신 실패는 공통 상위 타입으로도 잡힌다")
    void bothFailureTypesShareCommonParent() {
        // 되돌리기처럼 원인과 무관하게 정리만 하는 경로가 부모 타입 하나로 잡을 수 있어야 한다
        assertThat(new S3ValidationException("검증")).isInstanceOf(S3UploadException.class);
        assertThat(new S3CommunicationException("통신")).isInstanceOf(S3UploadException.class);
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 검증 실패이고 통신 실패가 아니다")
    void disallowedExtensionIsValidationNotCommunication() {
        assertThatThrownBy(() -> s3Uploader.upload(photo("animation.gif"), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3ValidationException.class)
            .isNotInstanceOf(S3CommunicationException.class);
    }

    @Test
    @DisplayName("업로드한 URL은 요청 순서를 유지하고 카테고리 경로 아래에 저장된다")
    void keepsRequestOrderAndCategoryDirectory() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        List<String> urls = s3Uploader.uploadAll(photos(3), FileCategory.ACCIDENT_PHOTO);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(3)).putObject(captor.capture(), any(RequestBody.class));

        List<String> keys = captor.getAllValues().stream().map(PutObjectRequest::key).toList();
        assertThat(keys).allMatch(key -> key.startsWith(FileCategory.ACCIDENT_PHOTO.getDirectory() + "/"));
        assertThat(urls).containsExactlyElementsOf(keys.stream().map(k -> "https://sago-test.s3.ap-northeast-2.amazonaws.com/" + k).toList());
    }
}
