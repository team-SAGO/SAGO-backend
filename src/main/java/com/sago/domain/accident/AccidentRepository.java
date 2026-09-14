package com.sago.domain.accident;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccidentRepository extends JpaRepository<Accident, Long> {

    List<Accident> findByUser_UserIdOrderByOccurredAtDesc(Long userId);

    /**
     * 사고 행에 쓰기 락을 건다.
     *
     * 사고에 딸린 자료를 "없으면 만든다" 식으로 처리할 때, 확인과 생성 사이에 다른 요청이
     * 끼어들면 같은 자료가 두 벌 만들어진다. 사고 행을 잠가 그 구간을 한 번에 하나만 지나가게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Accident a where a.accidentId = :accidentId")
    Optional<Accident> findByIdForUpdate(@Param("accidentId") Long accidentId);
}
