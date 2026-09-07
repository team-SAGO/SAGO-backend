package com.sago.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SocialAuthRepository extends JpaRepository<SocialAuth, Long> {

    /**
     * 소셜 계정으로 연결된 회원을 함께 가져온다.
     *
     * user가 지연 로딩이라 fetch join 없이 조회하면, 트랜잭션 밖에서 getUser()를 만지는 순간
     * LazyInitializationException이 난다. 로그인 흐름은 외부 HTTP 호출을 트랜잭션 밖에 두느라
     * 조회 트랜잭션이 짧게 끝나므로 여기서 미리 함께 읽어와야 한다.
     */
    @Query("select sa from SocialAuth sa join fetch sa.user "
        + "where sa.provider = :provider and sa.providerUserId = :providerUserId")
    Optional<SocialAuth> findWithUserByProviderAndProviderUserId(
        @Param("provider") AuthProvider provider,
        @Param("providerUserId") String providerUserId);
}
