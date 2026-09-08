package com.sago.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 토큰 하나를 지우고 지워진 행 수를 돌려준다.
     *
     * 파생 삭제(deleteByTokenHash)가 아니라 벌크 삭제로 둔 이유는 두 가지다.
     * - 같은 토큰으로 동시에 두 요청이 들어오면 파생 삭제는 양쪽이 엔티티를 읽은 뒤 지우려 해서,
     *   진 쪽이 0건 삭제를 만나 Hibernate가 StaleStateException을 던진다. 벌크 삭제는 그냥 0을 돌려준다.
     * - 행 수가 곧 "내가 이 토큰을 차지했는가"의 답이 되어, 경합의 승자를 DB가 정해준다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.user.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
