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
            .isInstanceOf(S3UploadException.class)
            .hasCause(failure);

        // 첫 장만 올라갔으므로 삭제도 한 번
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
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
            .isInstanceOf(S3UploadException.class)
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
            .isInstanceOf(S3UploadException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("개수 상한을 넘으면 한 장도 올리지 않는다")
    void uploadsNothingWhenCountExceedsMax() {
        int overLimit = FileCategory.ACCIDENT_PHOTO.getMaxCount() + 1;

        assertThatThrownBy(() -> s3Uploader.uploadAll(photos(overLimit), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3UploadException.class);

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
    @DisplayName("빈 목록은 업로드를 시도하지 않는다")
    void rejectsEmptyFileList() {
        assertThatThrownBy(() -> s3Uploader.uploadAll(List.of(), FileCategory.ACCIDENT_PHOTO))
            .isInstanceOf(S3UploadException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
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
