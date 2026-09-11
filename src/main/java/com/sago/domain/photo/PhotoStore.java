package com.sago.domain.photo;

import com.sago.domain.photo.dto.PhotoResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사진 정보 저장의 DB 작업만 담당한다.
 *
 * {@link PhotoService}에서 분리한 이유는 S3 업로드를 트랜잭션 밖에 두기 위해서다. 사진은 최대
 * 10장이라 업로드가 길어질 수 있는데, 그동안 DB 커넥션을 붙잡으면 안 된다. 같은 빈 안에서
 * 나눠두면 프록시가 적용되지 않아 별도 빈으로 뺐다(프로필 이미지·체크리스트와 같은 구조).
 */
@Component
public class PhotoStore {

    private final PhotoRepository photoRepository;

    public PhotoStore(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    /** 한 요청의 사진을 한 트랜잭션으로 저장한다. 일부만 저장된 채로 남지 않는다. */
    @Transactional
    public List<PhotoResponse> saveAll(List<Photo> photos) {
        return photoRepository.saveAll(photos).stream()
            .map(PhotoResponse::from)
            .toList();
    }
}
