package com.sago.domain.auth;

import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.SocialAuth;
import com.sago.domain.user.SocialAuthRepository;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthUserInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 소셜 계정 조회와 회원 등록의 DB 작업만 담당한다.
 *
 * AuthService에서 분리한 이유가 두 가지 있다.
 * 1. 로그인 과정의 외부 HTTP 호출을 트랜잭션 밖에 두기 위해서다. 소셜 서버 응답이 느리면
 *    (최악의 경우 connect 5초 + read 10초가 두 번) 그 시간만큼 DB 커넥션을 붙잡게 된다.
 * 2. 등록 실패 시의 재조회를 별도 트랜잭션으로 돌리기 위해서다. 유니크 제약 위반이 나면 그
 *    트랜잭션은 rollback-only로 표시되어, 같은 트랜잭션 안에서는 재조회를 해도 커밋 시점에
 *    UnexpectedRollbackException으로 터진다. 호출자가 별개의 빈을 통해 부르므로 프록시가
 *    적용되어 메서드마다 트랜잭션이 따로 열린다.
 */
@Component
public class SocialAccountRegistrar {

    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;

    public SocialAccountRegistrar(UserRepository userRepository,
                                   SocialAuthRepository socialAuthRepository) {
        this.userRepository = userRepository;
        this.socialAuthRepository = socialAuthRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findUser(AuthProvider provider, String providerUserId) {
        return socialAuthRepository
            .findWithUserByProviderAndProviderUserId(provider, providerUserId)
            .map(SocialAuth::getUser);
    }

    /**
     * 회원과 소셜 연결 정보를 함께 만든다.
     *
     * 같은 소셜 계정으로 동시에 두 번 로그인이 들어오면 유니크 제약에 걸려
     * DataIntegrityViolationException이 나간다. 이때 이 트랜잭션은 온전히 롤백되므로
     * 회원만 남는 일은 없고, 호출자가 새 트랜잭션으로 재조회하면 먼저 커밋된 회원을 얻는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User register(AuthProvider provider, OAuthUserInfo userInfo) {
        User user = userRepository.save(User.builder()
            .email(resolveEmail(provider, userInfo))
            .nickname(userInfo.nickname())
            .build());

        socialAuthRepository.save(SocialAuth.builder()
            .user(user)
            .provider(provider)
            .providerUserId(userInfo.providerUserId())
            .build());

        return user;
    }

    /**
     * 카카오는 이메일이 선택 동의 항목이라 내려오지 않을 수 있다.
     * email 컬럼이 NOT NULL이므로 제공자 식별자를 이용한 자리표시 주소를 채워두고,
     * 이후 프로필 설정에서 실제 주소를 받는다.
     *
     * 이 주소가 실재하지 않는다는 사실은 User.hasPlaceholderEmail()로 판별할 수 있다.
     */
    private String resolveEmail(AuthProvider provider, OAuthUserInfo userInfo) {
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            return userInfo.email();
        }
        return provider.name().toLowerCase() + "_" + userInfo.providerUserId()
            + User.PLACEHOLDER_EMAIL_DOMAIN;
    }
}
