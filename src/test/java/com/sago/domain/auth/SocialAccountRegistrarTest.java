package com.sago.domain.auth;

import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.SocialAuthRepository;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동시 로그인 시의 중복 가입 복구를 실제 DB 제약으로 검증한다.
 *
 * 목 기반 테스트로는 이 경로를 잡을 수 없다. 문제의 핵심이 "유니크 제약 위반 이후에도
 * 재조회가 가능한가"인데, 그건 트랜잭션이 실제로 어떻게 열리고 롤백되는지에 달려 있기 때문이다.
 * 그래서 이 테스트만 @Transactional 없이 실제 커밋이 일어나도록 두고, 뒷정리를 직접 한다.
 */
@SpringBootTest
class SocialAccountRegistrarTest {

    private static final AuthProvider PROVIDER = AuthProvider.KAKAO;
    private static final String PROVIDER_USER_ID = "kakao-concurrent-1234";

    @Autowired
    private SocialAccountRegistrar registrar;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAuthRepository socialAuthRepository;

    @Test
    @DisplayName("같은 소셜 계정으로 두 번 등록하면 두 번째는 제약 위반이 나고, 재조회로 첫 회원을 얻는다")
    void duplicateRegistrationIsRecoverableByRefetch() {
        OAuthUserInfo userInfo =
            new OAuthUserInfo(PROVIDER_USER_ID, "rider@example.com", "라이더");

        try {
            User first = registrar.register(PROVIDER, userInfo);

            assertThatThrownBy(() -> registrar.register(PROVIDER, userInfo))
                .isInstanceOf(DataIntegrityViolationException.class);

            // 등록 트랜잭션이 따로 열렸으므로, 실패 이후에도 조회가 정상 동작해야 한다.
            // 트랜잭션을 공유하면 여기서 조회가 실패하거나 UnexpectedRollbackException이 난다.
            Optional<User> recovered = registrar.findUser(PROVIDER, PROVIDER_USER_ID);

            assertThat(recovered).isPresent();
            assertThat(recovered.get().getUserId()).isEqualTo(first.getUserId());

            // 실패한 등록이 회원만 남기고 롤백되지 않았는지 확인한다.
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(socialAuthRepository.count()).isEqualTo(1);
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("소셜에서 이메일을 못 받으면 자리표시 주소로 가입되고, 그 사실을 판별할 수 있다")
    void placeholderEmailIsMarked() {
        OAuthUserInfo noEmail = new OAuthUserInfo("kakao-no-email-9999", null, null);

        try {
            User user = registrar.register(PROVIDER, noEmail);

            assertThat(user.getEmail()).endsWith(User.PLACEHOLDER_EMAIL_DOMAIN);
            assertThat(user.hasPlaceholderEmail()).isTrue();
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("실제 이메일로 가입한 회원은 자리표시자로 판별되지 않는다")
    void realEmailIsNotMarkedAsPlaceholder() {
        OAuthUserInfo withEmail =
            new OAuthUserInfo("google-1111", "rider@gmail.com", "라이더");

        try {
            User user = registrar.register(AuthProvider.GOOGLE, withEmail);

            assertThat(user.getEmail()).isEqualTo("rider@gmail.com");
            assertThat(user.hasPlaceholderEmail()).isFalse();
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }
}
