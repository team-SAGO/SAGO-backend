package com.sago.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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

    /**
     * 기준 시각 전에 만료된 토큰을 한 번에 지우고 지워진 행 수를 돌려준다.
     *
     * expires_at에 인덱스를 두지 않았다. 하루 한 번 도는 정리 작업에서만 쓰는 조건이고, 이 정리 덕분에
     * 테이블은 "최근 14일 안에 발급된 토큰 + 정리를 기다리는 하루치" 이상으로 커지지 않는다.
     * 반면 인덱스는 로그인·재발급마다 쓰기 비용을 더한다. 자주 쓰는 경로를 느리게 해서
     * 드문 경로를 빠르게 할 이유가 없다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}
