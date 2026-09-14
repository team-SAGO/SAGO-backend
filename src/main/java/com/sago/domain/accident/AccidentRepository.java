package com.sago.domain.accident;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccidentRepository extends JpaRepository<Accident, Long> {

    List<Accident> findByUser_UserIdOrderByOccurredAtDesc(Long userId);

    /**
     * 회원의 가장 최근 사고 중 주어진 상태이고 기준 시각 이후에 만들어진 것.
     *
     * 인덱스를 따로 두지 않았다. (user_id, occurred_at) 인덱스의 user_id로 회원 행을 먼저 좁히고,
     * 한 회원의 사고는 많아야 수십 건이라 나머지 조건은 그 안에서 걸러도 충분하다.
     */
    Optional<Accident> findFirstByUser_UserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
        Long userId, AccidentStatus status, LocalDateTime createdAfter);

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
