package com.sago.domain.auth;

import com.sago.domain.auth.SocialAccountRegistrar.SignUp;
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
            new OAuthUserInfo(PROVIDER_USER_ID, "rider@example.com", "라이더", true);

        try {
            User first = registrar.registerOrLink(PROVIDER, userInfo).user();

            assertThatThrownBy(() -> registrar.registerOrLink(PROVIDER, userInfo))
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
        OAuthUserInfo noEmail = new OAuthUserInfo("kakao-no-email-9999", null, null, false);

        try {
            User user = registrar.registerOrLink(PROVIDER, noEmail).user();

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
            new OAuthUserInfo("google-1111", "rider@gmail.com", "라이더", true);

        try {
            User user = registrar.registerOrLink(AuthProvider.GOOGLE, withEmail).user();

            assertThat(user.getEmail()).isEqualTo("rider@gmail.com");
            assertThat(user.hasPlaceholderEmail()).isFalse();
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("양쪽 모두 검증된 같은 이메일이면 새로 가입하지 않고 기존 회원에 연결한다")
    void linksWhenBothEmailsAreVerified() {
        try {
            User kakaoUser = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-link-1", "same@example.com", "라이더", true)).user();

            SignUp googleLogin = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-link-1", "same@example.com", "구글이름", true));

            assertThat(googleLogin.linked()).isTrue();
            assertThat(googleLogin.user().getUserId()).isEqualTo(kakaoUser.getUserId());
            assertThat(userRepository.count()).isEqualTo(1);
            // 이제 구글로 로그인해도 같은 회원이 나온다
            assertThat(registrar.findUser(AuthProvider.GOOGLE, "google-link-1"))
                .get().extracting(User::getUserId).isEqualTo(kakaoUser.getUserId());
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("대소문자만 다른 같은 이메일도 같은 사람으로 보고 연결한다")
    void linksIgnoringEmailCase() {
        try {
            // 제공자마다 저장된 표기가 다를 수 있다. 메일함은 같으므로 같은 사람이다.
            User kakaoUser = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-case", "Rider@Example.com", "라이더", true)).user();

            SignUp googleLogin = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-case", "rider@example.com", "라이더", true));

            assertThat(googleLogin.linked()).isTrue();
            assertThat(googleLogin.user().getUserId()).isEqualTo(kakaoUser.getUserId());
            assertThat(userRepository.count()).isEqualTo(1);
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("들어오는 계정의 이메일이 미검증이면 연결하지 않고 새로 가입한다")
    void doesNotLinkUnverifiedIncomingEmail() {
        try {
            User victim = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-victim", "victim@example.com", "피해자", true)).user();

            // 피해자 이메일을 적었지만 소유를 확인받지 못한 계정
            SignUp attacker = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-attacker", "victim@example.com", "공격자", false));

            assertThat(attacker.linked()).isFalse();
            assertThat(attacker.user().getUserId()).isNotEqualTo(victim.getUserId());
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("기존 회원의 이메일이 미검증이면 검증된 계정으로 와도 연결하지 않는다 — 먼저 가입해 두는 탈취를 막는다")
    void doesNotLinkToUnverifiedExistingAccount() {
        try {
            // 공격자가 검증 없이 피해자 이메일로 먼저 가입
            User attacker = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-preclaim", "victim@example.com", "공격자", false)).user();

            // 나중에 피해자가 검증된 계정으로 로그인
            SignUp victim = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-real-owner", "victim@example.com", "피해자", true));

            assertThat(victim.linked()).isFalse();
            assertThat(victim.user().getUserId()).isNotEqualTo(attacker.getUserId());
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("탈퇴한 회원에는 연결하지 않는다")
    void doesNotLinkToWithdrawnAccount() {
        try {
            User withdrawn = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-left", "left@example.com", "탈퇴", true)).user();
            withdrawn.withdraw();
            userRepository.save(withdrawn);

            SignUp comeback = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-comeback", "left@example.com", "재가입", true));

            assertThat(comeback.linked()).isFalse();
            assertThat(comeback.user().getUserId()).isNotEqualTo(withdrawn.getUserId());
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    @Test
    @DisplayName("가입할 때 이메일 검증 여부를 남기고, 자리표시 주소는 검증된 것으로 남기지 않는다")
    void storesEmailVerificationOnRegistration() {
        try {
            User verified = registrar.registerOrLink(AuthProvider.GOOGLE,
                new OAuthUserInfo("google-v", "v@example.com", null, true)).user();
            User unverified = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-u", "u@example.com", null, false)).user();
            User placeholder = registrar.registerOrLink(AuthProvider.KAKAO,
                new OAuthUserInfo("kakao-p", null, null, true)).user();

            assertThat(verified.isEmailVerified()).isTrue();
            assertThat(unverified.isEmailVerified()).isFalse();
            assertThat(placeholder.isEmailVerified()).isFalse();
        } finally {
            socialAuthRepository.deleteAll();
            userRepository.deleteAll();
        }
    }
}
