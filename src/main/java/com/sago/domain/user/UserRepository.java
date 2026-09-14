package com.sago.domain.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 탈퇴하지 않은 회원 행에 쓰기 락을 건다.
     *
     * 회원 단위로 "있으면 돌려주고 없으면 만든다"를 할 때 쓴다. 확인과 생성 사이에 같은 회원의
     * 다른 요청이 끼어들면 둘 다 "없음"을 보고 각자 만들기 때문에, 회원 행을 잠가 한 번에 하나만
     * 그 구간을 지나가게 한다. 아직 만들어지지 않은 행은 잠글 수 없어 이미 있는 회원 행을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId and u.deletedAt is null")
    Optional<User> findActiveByIdForUpdate(@Param("userId") Long userId);
}
