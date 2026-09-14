package com.sago.domain.photo;

import com.sago.domain.photo.dto.PhotoMetadataRequest;
import com.sago.domain.photo.dto.PhotoResponse;
import com.sago.domain.photo.dto.PhotoUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 사고 현장 사진 (Step 6). 사고 하위 리소스다. */
@RestController
@RequestMapping("/api/accidents/{accidentId}/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * 사진 업로드. 최대 10장.
     *
     * {@code photos} 파트에 파일을, {@code metadata} 파트에 장별 정보를 같은 순서로 담는다.
     * {@code metadata}는 생략할 수 있고, 보낼 때는 파트의 Content-Type이 {@code application/json}이어야 한다.
     *
     * 일부가 실패해도 200이다. 응답의 {@code failures}로 실패한 사진을 확인한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoUploadResponse upload(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long accidentId,
                                      @RequestPart("photos") List<MultipartFile> photos,
                                      @Valid @RequestPart(value = "metadata", required = false)
                                      PhotoMetadataRequest metadata) {
        return photoService.upload(userId, accidentId, photos, metadata);
    }

    @GetMapping
    public List<PhotoResponse> getPhotos(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long accidentId) {
        return photoService.getPhotos(userId, accidentId);
    }
}
