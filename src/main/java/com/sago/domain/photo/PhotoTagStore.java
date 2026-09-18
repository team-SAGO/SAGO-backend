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
 * 체크리스트(saveIfAbsent)와 달리 잠금이 필요 없다. 재태깅은 "없으면 만든다"가 아니라
 * "지우고 다시 만든다"라 두 요청이 겹쳐도 마지막에 커밋한 결과가 그대로 남을 뿐, 중복이
 * 쌓이지 않는다.
 */
@Component
public class PhotoTagStore {

    private final PhotoTagRepository photoTagRepository;

    public PhotoTagStore(PhotoTagRepository photoTagRepository) {
        this.photoTagRepository = photoTagRepository;
    }

    @Transactional
    public List<PhotoTag> replace(Long photoId, List<PhotoTag> tags) {
        photoTagRepository.deleteByPhoto_PhotoId(photoId);
        return photoTagRepository.saveAll(tags);
    }
}
