package com.sago.global.time;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 클라이언트가 보고한 시각(사고 발생 시각, 신고 시각 등)을 서버에서 받아들이는 규칙.
 *
 * 사고 현장은 통신이 불안정해 요청이 늦게 도착할 수 있으므로, 서버가 받은 시각보다
 * 사용자가 실제로 행동한 시각을 신뢰한다. 다만 기기 시간이 틀어져 들어온 미래 값은
 * 경위서·보험 서류에 그대로 실리면 사후에 바로잡기 어려워 거부한다.
 *
 * 여러 곳에서 같은 규칙을 쓰므로 한 곳에 둔다. 따로 두면 허용 범위가 한쪽만 바뀌어
 * 같은 사고의 시각들이 서로 다른 기준으로 검증된다.
 */
public final class ReportedTime {

    /**
     * 미래로 허용하는 범위. 클라이언트와 서버의 시계가 몇 초 어긋나는 건 흔한 일이라,
     * 딱 잘라 거부하면 정상 요청이 막힌다.
     */
    public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private ReportedTime() {
    }

    /**
     * 보고된 시각을 정한다. 보내지 않았으면 지금을 쓴다.
     *
     * @param reported 클라이언트가 보낸 시각. 없으면 null.
     * @param what     오류 메시지에 쓸 항목 이름 (예: "사고 발생 시각")
     * @throws IllegalArgumentException 허용 범위를 넘은 미래 시각인 경우
     */
    public static LocalDateTime resolve(LocalDateTime reported, String what) {
        if (reported == null) {
            return LocalDateTime.now();
        }
        if (reported.isAfter(LocalDateTime.now().plus(FUTURE_TOLERANCE))) {
            throw new IllegalArgumentException(what + "은 미래일 수 없습니다.");
        }
        return reported;
    }
}
