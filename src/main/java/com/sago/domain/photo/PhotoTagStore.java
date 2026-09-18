package com.sago.domain.photo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사진 태그의 DB 작업만 담당한다.
 *
 * {@link PhotoTaggingService}에서 분리한 이유는 Gemini Vision 호출을 트랜잭션 밖에
 * 두기 위해서다. 같은 빈 안에서 메서드만 나누면 프록시가 적용되지 않아 별도 빈으로 뺐다
 * (#27의 SocialAccountRegistrar, #38의 ProfileImageStore, #47의 ChecklistStore와 같은 구조).
 *
 * 재태깅은 "없으면 만든다"가 아니라 "지우고 다시 만든다"라 체크리스트(saveIfAbsent)와는 다른
 * 이유로 잠금이 필요하다. 두 재태깅이 겹치면 각자 삭제 전의 스냅샷만 보고 지우기 때문에,
 * 서로의 새 태그는 지우지 못하고 자기 태그만 남겨 결과가 합쳐질 수 있다. 사진 행에 락을 걸어
 * 교체 구간을 한 번에 하나만 지나가게 한다.
 */
@Component
public class PhotoTagStore {

    private final PhotoTagRepository photoTagRepository;
    private final PhotoRepository photoRepository;

    public PhotoTagStore(PhotoTagRepository photoTagRepository, PhotoRepository photoRepository) {
        this.photoTagRepository = photoTagRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional
    public List<PhotoTag> replace(Long photoId, List<PhotoTag> tags) {
        // 반환값을 쓰지 않는 호출이다. 사진 행에 쓰기 락을 걸어 아래 삭제·저장 구간을
        // 한 번에 하나만 지나가게 하는 것이 목적이라, 조회 결과 자체는 필요 없다.
        photoRepository.findByIdForUpdate(photoId);

        photoTagRepository.deleteByPhoto_PhotoId(photoId);
        return photoTagRepository.saveAll(tags);
    }
}
