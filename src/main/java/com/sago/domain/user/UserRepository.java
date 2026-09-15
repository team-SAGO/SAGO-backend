package com.sago.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 이메일이 검증된 채로 가입한, 탈퇴하지 않은 회원 중 가장 먼저 가입한 회원 (#33).
     *
     * 같은 이메일의 검증된 회원이 여럿일 수 있다(이 기능 전에 제공자별로 따로 가입한 경우). 모두 같은
     * 사람이므로 어느 쪽에 붙여도 탈취는 아니고, 결과가 매번 같도록 가장 오래된 회원을 고른다.
     */
    Optional<User> findFirstByEmailAndEmailVerifiedTrueAndDeletedAtIsNullOrderByCreatedAtAscUserIdAsc(String email);
}
