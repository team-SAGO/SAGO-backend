package com.sago.domain.photo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByAccident_AccidentIdOrderByCreatedAtAsc(Long accidentId);

    /**
     * 사진 행에 쓰기 락을 건다.
     *
     * 같은 사진의 태그 교체(delete+saveAll)가 겹치면, 두 트랜잭션이 서로의 새 태그를 보지 못한 채
     * 각자 저장해 두 결과의 합집합이 남을 수 있다. 사진 행을 잠가 교체 구간을 한 번에 하나만
     * 지나가게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Photo p where p.photoId = :photoId")
    Optional<Photo> findByIdForUpdate(@Param("photoId") Long photoId);
}
