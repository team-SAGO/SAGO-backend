package com.sago.domain.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 refresh 토큰 행을 주기적으로 정리한다 (#49).
 *
 * 로그인할 때 그 회원의 만료 행을 함께 지우는 방법도 있었지만, 그러면 다시 접속하지 않는 회원의
 * 행은 영영 남는다. 정리할 대상이 가장 많은 쪽이 바로 그런 회원이라 스케줄러로 했다.
 *
 * 서버가 여러 대여도 따로 잠금을 두지 않았다. 하는 일이 "만료된 행 삭제" 하나라 두 대가 동시에
 * 돌아도 한쪽이 지우고 다른 쪽은 0건을 지울 뿐, 결과가 달라지지 않는다.
 *
 * 시각은 {@code jwt.refresh-cleanup-cron}으로 바꿀 수 있고, {@code -}를 넣으면 꺼진다(테스트에서 끈다).
 * 서버 시간대와 관계없이 사용이 적은 새벽에 돌도록 한국 시간으로 고정했다.
 */
@Slf4j
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenStore refreshTokenStore;

    public RefreshTokenCleanupJob(RefreshTokenStore refreshTokenStore) {
        this.refreshTokenStore = refreshTokenStore;
    }

    @Scheduled(cron = "${jwt.refresh-cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredTokens() {
        int deleted = refreshTokenStore.deleteExpired();
        log.info("만료된 refresh 토큰 {}건을 정리했습니다.", deleted);
    }
}
