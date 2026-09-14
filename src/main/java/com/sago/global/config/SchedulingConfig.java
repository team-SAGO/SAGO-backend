package com.sago.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다.
 *
 * 애플리케이션 클래스에 붙이지 않고 따로 둔 이유는, 설정이 한곳에 모여 있어야 "이 서버가 주기 작업을
 * 돌린다"는 사실이 눈에 띄기 때문이다. 현재 작업은 {@link com.sago.domain.auth.RefreshTokenCleanupJob} 하나다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
