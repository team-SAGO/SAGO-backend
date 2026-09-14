package com.sago.domain.photo;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.photo.dto.PhotoMetadata;
import com.sago.domain.photo.dto.PhotoMetadataRequest;
import com.sago.domain.photo.dto.PhotoResponse;
import com.sago.domain.photo.dto.PhotoUploadResponse;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.MultiUploadResult;
import com.sago.global.client.s3.MultiUploadResult.FailedUpload;
import com.sago.global.client.s3.S3Uploader;
import com.sago.global.time.ReportedTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사고 현장 사진 저장·조회 (Step 6).
 *
 * 업로드는 일부가 실패해도 성공한 사진을 남긴다(#39). 사고 현장은 시간이 지나면 흔적이 사라져
 * 재촬영이 불가능한 경우가 많기 때문이다. 그래서 요청 전체를 거부하는 검사는 "요청 자체가
 * 잘못된 경우"(남의 사고, 정보 개수 불일치, 좌표 범위 위반)로 한정하고, 모두 업로드 전에 한다.
 *
 * 업로드 메서드에는 트랜잭션을 걸지 않았다. S3 업로드는 롤백되지 않는 외부 호출이라
 * DB 저장만 {@link PhotoStore}의 짧은 트랜잭션에 맡기고, 저장이 실패하면 올린 파일을 지운다.
 */
@Slf4j
@Service
public class PhotoService {

    private final AccidentService accidentService;
    private final S3Uploader s3Uploader;
    private final PhotoStore photoStore;
    private final PhotoRepository photoRepository;

    public PhotoService(AccidentService accidentService, S3Uploader s3Uploader,
                        PhotoStore photoStore, PhotoRepository photoRepository) {
        this.accidentService = accidentService;
        this.s3Uploader = s3Uploader;
        this.photoStore = photoStore;
        this.photoRepository = photoRepository;
    }

    public PhotoUploadResponse upload(Long userId, Long accidentId, List<MultipartFile> files,
                                      PhotoMetadataRequest metadata) {
        // 남의 사고에 올린 사진을 도로 지우는 낭비를 막기 위해 업로드 전에 확인한다.
        Accident accident = accidentService.getOwnedAccident(userId, accidentId);
        List<PhotoMetadata> metadataByIndex = matchMetadata(files, metadata);

        MultiUploadResult result = s3Uploader.uploadAllowingPartial(files, FileCategory.ACCIDENT_PHOTO);
        if (result.uploadedUrls().isEmpty()) {
            return new PhotoUploadResponse(List.of(), result.failures());
        }

        List<PhotoResponse> saved;
        try {
            saved = photoStore.saveAll(toPhotos(accident, files.size(), metadataByIndex, result));
        } catch (RuntimeException e) {
            // 기록이 없으면 누구도 찾을 수 없는 파일이라 남겨둘 이유가 없다.
            // 짝 맞추기가 실패한 경우(toPhotos)도 여기서 함께 정리한다.
            result.uploadedUrls().forEach(this::deleteQuietly);
            throw e;
        }
        return new PhotoUploadResponse(saved, result.failures());
    }

    /** 사고의 사진. 올린 순서대로 나온다. */
    @Transactional(readOnly = true)
    public List<PhotoResponse> getPhotos(Long userId, Long accidentId) {
        accidentService.getOwnedAccident(userId, accidentId);

        return photoRepository.findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(accidentId).stream()
            .map(PhotoResponse::from)
            .toList();
    }

    /**
     * 파일 순서대로 장별 정보를 맞춘다. 정보를 보내지 않았으면 전부 빈 정보다.
     *
     * 개수가 다르면 어느 정보가 어느 사진 것인지 알 수 없어 요청을 거부한다. 틀린 위치·시각이
     * 다른 사진에 붙어 저장되는 것보다, 클라이언트 오류를 바로 드러내는 편이 낫다.
     */
    private List<PhotoMetadata> matchMetadata(List<MultipartFile> files, PhotoMetadataRequest metadata) {
        int fileCount = files == null ? 0 : files.size();
        if (metadata == null || metadata.photos() == null) {
            return Collections.nCopies(fileCount, PhotoMetadata.EMPTY);
        }
        if (metadata.photos().size() != fileCount) {
            throw new IllegalArgumentException(
                "사진 정보 개수가 사진 개수와 다릅니다. (사진 " + fileCount + "장, 정보 "
                    + metadata.photos().size() + "개)");
        }
        return metadata.photos().stream()
            .map(item -> item == null ? PhotoMetadata.EMPTY : item)
            .toList();
    }

    /**
     * 성공한 URL을 요청에서의 순번과 짝지어 엔티티로 만든다.
     *
     * 업로드 결과에는 성공한 파일의 순번이 없어, "실패한 순번을 뺀 나머지가 요청 순서대로"라는
     * {@link S3Uploader#uploadAllowingPartial}의 동작에 기대 짝을 맞춘다. 이 전제가 깨지면 위치와
     * 시각이 다른 사진에 붙어 저장되므로, 개수가 맞지 않으면 예외를 던져 저장하지 않는다.
     */
    private List<Photo> toPhotos(Accident accident, int fileCount, List<PhotoMetadata> metadataByIndex,
                                 MultiUploadResult result) {
        if (result.uploadedUrls().size() + result.failures().size() != fileCount) {
            throw new IllegalStateException(
                "업로드 결과 개수가 요청과 맞지 않습니다. (요청 " + fileCount + ", 성공 "
                    + result.uploadedUrls().size() + ", 실패 " + result.failures().size() + ")");
        }

        Set<Integer> failedIndexes = result.failures().stream()
            .map(FailedUpload::index)
            .collect(Collectors.toSet());
        Iterator<String> uploadedUrls = result.uploadedUrls().iterator();

        List<Photo> photos = new ArrayList<>(result.uploadedUrls().size());
        for (int index = 0; index < fileCount; index++) {
            if (failedIndexes.contains(index)) {
                continue;
            }
            PhotoMetadata info = metadataByIndex.get(index);
            photos.add(Photo.builder()
                .accident(accident)
                .fileUrl(uploadedUrls.next())
                .category(info.category())
                .latitude(info.latitude())
                .longitude(info.longitude())
                .takenAt(ReportedTime.discardIfFuture(info.takenAt()))
                .build());
        }
        return photos;
    }

    /**
     * 정리용 삭제. 여기서 터진 예외로 원래 실패 원인이 가려지면 안 되므로 삼키되,
     * 삼키기만 하면 고아 파일이 생긴 순간을 아무도 모르게 되므로 로그로 남긴다.
     */
    private void deleteQuietly(String fileUrl) {
        try {
            s3Uploader.delete(fileUrl);
        } catch (RuntimeException e) {
            log.warn("사진 저장 실패 후 파일 삭제 실패, S3에 고아 파일이 남습니다: {}", fileUrl, e);
        }
    }
}
