package com.sago.domain.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 이메일이 검증된 채로 가입한, 탈퇴하지 않은 회원 (#33). 가장 먼저 가입한 회원이 앞에 온다.
     *
     * 대소문자를 무시하고 비교한다. PostgreSQL의 {@code =}는 대소문자를 구분해서, 그대로 비교하면
     * 구글이 준 {@code rider@example.com}과 카카오가 준 {@code Rider@Example.com}이 다른 사람이 된다.
     * 그러면 이 기능이 풀려던 "같은 사람이 두 제공자로 들어오는 경우"가 그대로 남는다.
     * 저장할 때 소문자로 바꾸지 않고 조회에서 맞추는 이유는, 이미 가입한 회원의 주소는 그대로이기 때문이다.
     *
     * 같은 이메일의 검증된 회원이 여럿일 수 있다(이 기능 전에 제공자별로 따로 가입한 경우). 모두 같은
     * 사람이므로 어느 쪽에 붙여도 탈취는 아니고, 결과가 매번 같도록 가장 오래된 회원을 고른다.
     *
     * 함수 인덱스(lower(email))가 있어야 인덱스를 타지만 JPA로는 만들 수 없다. 처음 보는 소셜 계정으로
     * 로그인할 때만 실행되고 회원 테이블도 크지 않아 지금은 문제가 없다 — 마이그레이션 도구(#44)가
     * 정해지면 그때 함수 인덱스를 추가하면 된다.
     */
    @Query("select u from User u "
        + "where lower(u.email) = lower(:email) and u.emailVerified = true and u.deletedAt is null "
        + "order by u.createdAt asc, u.userId asc")
    List<User> findVerifiedByEmailIgnoreCase(@Param("email") String email, Pageable pageable);
}
