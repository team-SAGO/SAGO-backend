package com.sago.global.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportedTimeTest {

    @Test
    @DisplayName("보내지 않으면 지금 시각을 쓴다")
    void defaultsToNow() {
        LocalDateTime before = LocalDateTime.now();

        assertThat(ReportedTime.resolve(null, "연결 시각")).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("과거 시각은 그대로 쓴다")
    void keepsPastTime() {
        LocalDateTime past = LocalDateTime.of(2026, 9, 1, 12, 30);

        assertThat(ReportedTime.resolve(past, "연결 시각")).isEqualTo(past);
    }

    @Test
    @DisplayName("시계 오차 범위 안의 미래 시각은 허용한다")
    void allowsClockSkew() {
        LocalDateTime slightlyAhead = LocalDateTime.now().plusMinutes(1);

        assertThat(ReportedTime.resolve(slightlyAhead, "연결 시각")).isEqualTo(slightlyAhead);
    }

    @Test
    @DisplayName("허용 범위를 넘은 미래 시각은 항목 이름과 함께 거부한다")
    void rejectsFarFutureWithFieldName() {
        assertThatThrownBy(() -> ReportedTime.resolve(LocalDateTime.now().plusHours(1), "연결 시각"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("연결 시각은 미래일 수 없습니다.");
    }
}
