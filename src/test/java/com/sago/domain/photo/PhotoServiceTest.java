package com.sago.domain.photo;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.accident.AccidentType;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.MultiUploadResult;
import com.sago.global.client.s3.S3CommunicationException;
import com.sago.global.client.s3.S3Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 저장이 실패했을 때의 정리를 확인한다. 정상 경로에서는 실행되지 않아 깨져도 드러나지 않는다.
 */
class PhotoServiceTest {

    private static final String FIRST = "https://bucket/accidents/photos/first.jpg";
    private static final String SECOND = "https://bucket/accidents/photos/second.jpg";

    private S3Uploader s3Uploader;
    private PhotoStore photoStore;
    private PhotoService photoService;

    @BeforeEach
    void setUp() {
        AccidentService accidentService = mock(AccidentService.class);
        s3Uploader = mock(S3Uploader.class);
        photoStore = mock(PhotoStore.class);
        photoService = new PhotoService(accidentService, s3Uploader, photoStore, mock(PhotoRepository.class));

        when(accidentService.getOwnedAccident(1L, 10L)).thenReturn(Accident.builder()
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build());
    }

    @Test
    @DisplayName("DB 저장이 실패하면 올린 사진을 지우고 원래 예외를 전파한다")
    void deletesUploadedPhotosWhenSaveFails() {
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(List.of(FIRST, SECOND), List.of()));
        IllegalStateException failure = new IllegalStateException("DB 오류");
        when(photoStore.saveAll(anyList())).thenThrow(failure);

        assertThatThrownBy(() -> photoService.upload(1L, 10L, photos(2), null))
            .isSameAs(failure);

        // 기록이 없으면 누구도 찾을 수 없는 파일이다
        verify(s3Uploader).delete(FIRST);
        verify(s3Uploader).delete(SECOND);
    }

    @Test
    @DisplayName("정리 중 삭제가 실패해도 원래 예외가 가려지지 않는다")
    void keepsOriginalFailureWhenCleanupFails() {
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(List.of(FIRST, SECOND), List.of()));
        IllegalStateException failure = new IllegalStateException("DB 오류");
        when(photoStore.saveAll(anyList())).thenThrow(failure);
        doThrow(new S3CommunicationException("삭제 실패")).when(s3Uploader).delete(FIRST);

        assertThatThrownBy(() -> photoService.upload(1L, 10L, photos(2), null))
            .isSameAs(failure);

        // 첫 삭제가 실패해도 나머지는 계속 지운다
        verify(s3Uploader).delete(SECOND);
    }

    @Test
    @DisplayName("업로드 결과 개수가 요청과 맞지 않으면 정보를 엉뚱한 사진에 붙이지 않고 올린 파일을 지운다")
    void refusesToPairWhenResultDoesNotAddUp() {
        // 2장을 보냈는데 성공 1, 실패 0. 어느 파일이 성공했는지 알 수 없다.
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(List.of(FIRST), List.of()));

        assertThatThrownBy(() -> photoService.upload(1L, 10L, photos(2), null))
            .isInstanceOf(IllegalStateException.class);

        verify(photoStore, never()).saveAll(anyList());
        verify(s3Uploader).delete(FIRST);
    }

    private List<MultipartFile> photos(int count) {
        return IntStream.range(0, count)
            .<MultipartFile>mapToObj(i -> new MockMultipartFile("photos", "photo" + i + ".jpg", "image/jpeg",
                new byte[] {1, 2, 3}))
            .toList();
    }
}
