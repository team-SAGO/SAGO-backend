package com.sago.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /**
     * 사고의 사진을 올린 순서대로.
     *
     * 한 요청의 사진은 거의 같은 순간에 저장돼 created_at만으로는 순서가 보장되지 않는다.
     * 저장 순서대로 커지는 photo_id로 한 번 더 정렬해 요청 순서를 지킨다.
     */
    List<Photo> findByAccident_AccidentIdOrderByCreatedAtAscPhotoIdAsc(Long accidentId);
}
