package com.sago.domain.auth;

import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.SocialAuth;
import com.sago.domain.user.SocialAuthRepository;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthUserInfo;
import org.springframework.data.domain.PageRequest;
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
     * 처음 보는 소셜 계정을 회원에 붙인다. 같은 사람의 기존 회원이 확실하면 거기에 연결하고,
     * 아니면 회원을 새로 만든다 (#33).
     *
     * 이메일이 같다는 것만으로는 같은 사람이라고 볼 수 없어서, <b>양쪽 모두 검증된 이메일일 때만</b> 연결한다.
     * <ul>
     *   <li>들어오는 쪽이 미검증이면: 공격자가 피해자 이메일을 적은 계정으로 피해자 회원에 붙을 수 있다.</li>
     *   <li>기존 회원이 미검증이면: 공격자가 피해자 이메일로 먼저 가입해 두었다가, 피해자가 검증된 계정으로
     *       로그인하는 순간 공격자 회원에 붙어 피해자의 사고 기록이 공격자에게 쌓인다.</li>
     * </ul>
     * 탈퇴한 회원에는 연결하지 않는다. 탈퇴 회원 처리(#57)가 정해지지 않았고, 연결하면 곧바로 탈퇴 회원
     * 로그인 거부에 걸린다.
     *
     * <p><b>알려진 한계 두 가지</b> (#80)
     * <ul>
     *   <li>같은 이메일로 서로 다른 제공자에서 <b>거의 동시에 처음</b> 로그인하면, 양쪽 다 "기존 회원 없음"을
     *       보고 회원이 둘 만들어질 수 있다. email에 유니크 제약이 없어 DB가 막아주지 못한다. 결과는
     *       "연결되지 않음"이라 이 기능 이전과 같고 탈취로 이어지지는 않는다.</li>
     *   <li>연결 사실을 <b>원래 계정 주인에게</b> 따로 알릴 수단이 없다. 메일 같은 별도 채널 알림은
     *       인증에서 통상적인 방어인데, 지금은 발송 수단 자체가 없다.</li>
     * </ul>
     *
     * 같은 소셜 계정으로 동시에 두 번 로그인이 들어오면 유니크 제약에 걸려
     * DataIntegrityViolationException이 나간다. 이때 이 트랜잭션은 온전히 롤백되므로
     * 회원만 남는 일은 없고, 호출자가 새 트랜잭션으로 재조회하면 먼저 커밋된 회원을 얻는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SignUp registerOrLink(AuthProvider provider, OAuthUserInfo userInfo) {
        Optional<User> sameVerifiedPerson = findVerifiedAccount(userInfo);
        if (sameVerifiedPerson.isPresent()) {
            connect(sameVerifiedPerson.get(), provider, userInfo);
            return new SignUp(sameVerifiedPerson.get(), true);
        }

        boolean hasRealEmail = userInfo.email() != null && !userInfo.email().isBlank();
        User user = userRepository.save(User.builder()
            .email(resolveEmail(provider, userInfo))
            .nickname(userInfo.nickname())
            // 자리표시 주소는 실재하지 않으므로 검증된 것으로 남기지 않는다.
            .emailVerified(hasRealEmail && userInfo.emailVerified())
            .build());
        connect(user, provider, userInfo);

        return new SignUp(user, false);
    }

    private Optional<User> findVerifiedAccount(OAuthUserInfo userInfo) {
        if (!userInfo.emailVerified() || userInfo.email() == null || userInfo.email().isBlank()) {
            return Optional.empty();
        }
        return userRepository.findVerifiedByEmailIgnoreCase(userInfo.email(), PageRequest.of(0, 1))
            .stream()
            .findFirst();
    }

    private void connect(User user, AuthProvider provider, OAuthUserInfo userInfo) {
        socialAuthRepository.save(SocialAuth.builder()
            .user(user)
            .provider(provider)
            .providerUserId(userInfo.providerUserId())
            .build());
    }

    /**
     * @param user   로그인할 회원
     * @param linked 새로 만들지 않고 같은 이메일의 기존 회원에 연결했으면 true
     */
    public record SignUp(User user, boolean linked) {
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
