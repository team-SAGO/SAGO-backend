package com.sago.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정리 작업의 설정을 확인한다.
 *
 * 테스트 설정에서는 스케줄러를 꺼 두기 때문에, 운영 cron 값에 오타가 있으면 어떤 테스트도 실패하지 않고
 * 운영 서버가 뜰 때에야 기동 실패로 드러난다. 그래서 운영 값이 올바른 cron인지 따로 확인한다.
 */
class RefreshTokenCleanupJobTest {

    @Test
    @DisplayName("application.yml의 기본 정리 주기는 올바른 cron이다")
    void defaultCronInApplicationYmlIsValid() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        // 환경변수가 없을 때의 값(${JWT_REFRESH_CLEANUP_CRON:기본값}의 기본값)을 꺼낸다
        String cron = new PropertyPlaceholderHelper("${", "}", ":", '\\', true)
            .replacePlaceholders(properties.getProperty("jwt.refresh-cleanup-cron"), name -> null);

        assertThatCode(() -> CronExpression.parse(cron)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("설정이 없을 때 쓰는 코드상 기본 주기도 올바른 cron이다")
    void fallbackCronInAnnotationIsValid() throws NoSuchMethodException {
        String expression = RefreshTokenCleanupJob.class.getMethod("deleteExpiredTokens")
            .getAnnotation(Scheduled.class)
            .cron();
        String fallback = expression.substring(expression.indexOf(':') + 1, expression.lastIndexOf('}'));

        assertThatCode(() -> CronExpression.parse(fallback)).doesNotThrowAnyException();
        assertThat(expression).startsWith("${jwt.refresh-cleanup-cron:");
    }

    @Test
    @DisplayName("작업은 만료 토큰 정리를 호출한다")
    void jobDeletesExpiredTokens() {
        RefreshTokenStore store = mock(RefreshTokenStore.class);
        when(store.deleteExpired()).thenReturn(3);

        new RefreshTokenCleanupJob(store).deleteExpiredTokens();

        verify(store).deleteExpired();
    }
}
