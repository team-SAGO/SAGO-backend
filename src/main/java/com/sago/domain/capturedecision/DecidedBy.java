package com.sago.domain.capturedecision;

/**
 * 촬영 필요 여부를 누가 결정했는지 구분. AI 판단이 모호하거나 지연되면
 * 사용자 직접 선택(USER)으로 위임한다.
 */
public enum DecidedBy {
    AI,
    USER
}
