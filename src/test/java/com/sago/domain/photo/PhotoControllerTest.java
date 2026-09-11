package com.sago.domain.photo;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentRepository;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.MultiUploadResult;
import com.sago.global.client.s3.MultiUploadResult.FailedUpload;
import com.sago.global.client.s3.MultiUploadResult.FailureType;
import com.sago.global.client.s3.S3Uploader;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 권한, 장별 정보 짝 맞추기, 저장 결과를 확인한다.
 *
 * S3 업로더는 대체한다. 실제로 부르면 버킷이 필요한데 테스트 환경에는 없다.
 * 업로드 자체(부분 성공·파일명 정리)는 S3UploaderTest에서 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhotoControllerTest {

    private static final String ENDPOINT = "/api/accidents/{id}/photos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private S3Uploader s3Uploader;

    private String ownerToken;
    private String strangerToken;
    private Long accidentId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        ownerToken = jwtTokenProvider.createAccessToken(owner.getUserId());
        strangerToken = jwtTokenProvider.createAccessToken(stranger.getUserId());

        accidentId = accidentRepository.save(Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now().minusMinutes(30))
            .build()).getAccidentId();
    }

    @Test
    @DisplayName("사진과 장별 정보가 같은 순서로 저장되고, 파일 주소는 응답에 나가지 않는다")
    void savesPhotosWithMetadata() throws Exception {
        uploadSucceeds("https://bucket/accidents/photos/a.jpg", "https://bucket/accidents/photos/b.jpg");

        mockMvc.perform(uploadRequest(2, """
                {"photos":[
                  {"category":"번호판","latitude":37.5665,"longitude":126.9780,"takenAt":"2026-09-11T10:00:00"},
                  {"category":"파손 부위"}
                ]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos.length()").value(2))
            .andExpect(jsonPath("$.photos[0].category").value("번호판"))
            .andExpect(jsonPath("$.photos[0].latitude").value(37.5665))
            .andExpect(jsonPath("$.photos[0].takenAt").value("2026-09-11T10:00:00"))
            .andExpect(jsonPath("$.photos[1].category").value("파손 부위"))
            // 비공개 버킷의 주소는 열리지도 않고 오브젝트 키만 드러낸다
            .andExpect(jsonPath("$.photos[0].fileUrl").doesNotExist())
            .andExpect(jsonPath("$.failures").isEmpty());

        assertThat(photoRepository.findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(accidentId))
            .extracting(Photo::getFileUrl)
            .containsExactly("https://bucket/accidents/photos/a.jpg", "https://bucket/accidents/photos/b.jpg");
    }

    @Test
    @DisplayName("일부가 실패하면 성공한 사진만 저장하고, 정보는 실패한 사진을 건너뛰어 제 사진에 붙는다")
    void savesOnlySucceededPhotosWithTheirOwnMetadata() throws Exception {
        // 3장 중 가운데가 실패. 정보가 순서대로만 붙으면 "전경"이 두 번째 사진의 정보를 받게 된다.
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(
                List.of("https://bucket/accidents/photos/first.jpg", "https://bucket/accidents/photos/third.jpg"),
                List.of(new FailedUpload(1, "photo1.jpg", FailureType.STORAGE_ERROR,
                    "업로드에 실패했습니다. 다시 시도해주세요."))));

        mockMvc.perform(uploadRequest(3, """
                {"photos":[{"category":"번호판"},{"category":"파손 부위"},{"category":"전경"}]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos.length()").value(2))
            .andExpect(jsonPath("$.failures[0].index").value(1))
            .andExpect(jsonPath("$.failures[0].filename").value("photo1.jpg"))
            .andExpect(jsonPath("$.failures[0].type").value("STORAGE_ERROR"));

        assertThat(photoRepository.findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(accidentId))
            .extracting(Photo::getFileUrl, Photo::getCategory)
            .containsExactly(
                tuple("https://bucket/accidents/photos/first.jpg", "번호판"),
                tuple("https://bucket/accidents/photos/third.jpg", "전경"));
    }

    @Test
    @DisplayName("전부 실패하면 저장하지 않고 실패 목록만 돌려준다")
    void savesNothingWhenAllFail() throws Exception {
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(List.of(),
                List.of(new FailedUpload(0, "photo0.gif", FailureType.INVALID_FILE, "허용되지 않은 파일 형식입니다"))));

        mockMvc.perform(uploadRequest(1, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos").isEmpty())
            .andExpect(jsonPath("$.failures[0].type").value("INVALID_FILE"));

        assertThat(photoRepository.findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(accidentId)).isEmpty();
    }

    @Test
    @DisplayName("정보 없이 올려도 저장된다 — 촬영 시각은 올린 시각으로 채우지 않는다")
    void savesWithoutMetadata() throws Exception {
        uploadSucceeds("https://bucket/accidents/photos/a.jpg");

        mockMvc.perform(uploadRequest(1, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].category").doesNotExist())
            // 갤러리에서 나중에 올린 사진일 수 있어, 올린 시각을 촬영 시각으로 적으면 틀린 값이 된다
            .andExpect(jsonPath("$.photos[0].takenAt").doesNotExist())
            .andExpect(jsonPath("$.photos[0].createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("촬영 시각이 먼 미래면 사진은 저장하고 시각만 비운다")
    void keepsPhotoButDropsFutureTakenAt() throws Exception {
        uploadSucceeds("https://bucket/accidents/photos/a.jpg");
        String farFuture = LocalDateTime.now().plusDays(1).withNano(0).toString();

        // 사고·연결 시각과 달리 400이 아니다. 기기 시계 하나 때문에 재촬영할 수 없는 사진을 거부하지 않는다.
        mockMvc.perform(uploadRequest(1, """
                {"photos":[{"category":"전경","takenAt":"%s"}]}""".formatted(farFuture)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].category").value("전경"))
            .andExpect(jsonPath("$.photos[0].takenAt").doesNotExist());
    }

    @Test
    @DisplayName("정보 개수가 사진 개수와 다르면 한 장도 올리지 않고 400이다")
    void rejectsMismatchedMetadataBeforeUpload() throws Exception {
        mockMvc.perform(uploadRequest(2, """
                {"photos":[{"category":"번호판"}]}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(containsString("개수")));

        verify(s3Uploader, never()).uploadAllowingPartial(anyList(), any());
    }

    @Test
    @DisplayName("좌표가 범위를 벗어나면 한 장도 올리지 않고 400이다")
    void rejectsOutOfRangeCoordinatesBeforeUpload() throws Exception {
        mockMvc.perform(uploadRequest(1, """
                {"photos":[{"latitude":123.0,"longitude":126.9780}]}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(containsString("위도")));

        verify(s3Uploader, never()).uploadAllowingPartial(anyList(), any());
    }

    @Test
    @DisplayName("남의 사고에는 올릴 수 없고, 업로드도 시작하지 않는다")
    void strangerCannotUpload() throws Exception {
        mockMvc.perform(multipart(ENDPOINT, accidentId)
                .file(photoFile(0))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound());

        verify(s3Uploader, never()).uploadAllowingPartial(anyList(), any());
    }

    @Test
    @DisplayName("토큰 없이 올릴 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart(ENDPOINT, accidentId).file(photoFile(0)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사진 목록은 올린 순서대로 나온다")
    void listsInUploadOrder() throws Exception {
        uploadSucceeds("https://bucket/accidents/photos/1.jpg", "https://bucket/accidents/photos/2.jpg",
            "https://bucket/accidents/photos/3.jpg");
        mockMvc.perform(uploadRequest(3, """
                {"photos":[{"category":"첫째"},{"category":"둘째"},{"category":"셋째"}]}"""))
            .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT, accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("첫째"))
            .andExpect(jsonPath("$[1].category").value("둘째"))
            .andExpect(jsonPath("$[2].category").value("셋째"));
    }

    @Test
    @DisplayName("남의 사고 사진은 조회할 수 없다")
    void strangerCannotList() throws Exception {
        mockMvc.perform(get(ENDPOINT, accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound());
    }

    private void uploadSucceeds(String... urls) {
        when(s3Uploader.uploadAllowingPartial(anyList(), eq(FileCategory.ACCIDENT_PHOTO)))
            .thenReturn(new MultiUploadResult(List.of(urls), List.of()));
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(int photoCount, String metadataJson) {
        MockMultipartHttpServletRequestBuilder request = multipart(ENDPOINT, accidentId);
        for (int i = 0; i < photoCount; i++) {
            request.file(photoFile(i));
        }
        if (metadataJson != null) {
            request.file(new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE,
                metadataJson.getBytes(StandardCharsets.UTF_8)));
        }
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken);
        return request;
    }

    private MockMultipartFile photoFile(int index) {
        return new MockMultipartFile("photos", "photo" + index + ".jpg", "image/jpeg", new byte[] {1, 2, 3});
    }
}
