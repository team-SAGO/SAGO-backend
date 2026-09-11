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
        if (isBeyondTolerance(reported)) {
            throw new IllegalArgumentException(what + "은 미래일 수 없습니다.");
        }
        return reported;
    }

    /**
     * 보고된 시각을 받되, 믿을 수 없는 미래 값이면 거부하지 않고 버린다(null).
     *
     * 시각보다 그 시각이 붙은 자료가 더 중요할 때 쓴다. 사고 사진이 그렇다 — 재촬영할 수 없는
     * 사진을 기기 시계 하나 때문에 거부하면 복구할 수 없는 손실이 된다. 틀린 시각을 저장하는 것도
     * 서류에 그대로 실려 곤란하므로, "모름"으로 남긴다.
     *
     * {@link #resolve}와 달리 보내지 않았을 때 지금을 채우지 않는다. 사진은 갤러리에서 나중에
     * 올릴 수도 있어, 올린 시각을 촬영 시각으로 적으면 틀린 값이 된다.
     *
     * @return 받아들일 수 있는 시각. 보내지 않았거나 허용 범위를 넘은 미래면 null.
     */
    public static LocalDateTime discardIfFuture(LocalDateTime reported) {
        if (reported == null || isBeyondTolerance(reported)) {
            return null;
        }
        return reported;
    }

    private static boolean isBeyondTolerance(LocalDateTime reported) {
        return reported.isAfter(LocalDateTime.now().plus(FUTURE_TOLERANCE));
    }
}
