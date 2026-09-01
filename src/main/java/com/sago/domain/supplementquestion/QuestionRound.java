package com.sago.domain.supplementquestion;

/**
 * 보완 질문의 회차. 최초 진술 직후 생성되면 INITIAL, Vision 태깅 이후
 * 추가 확인이 필요해 재진행되면 ADDITIONAL.
 */
public enum QuestionRound {
    INITIAL,
    ADDITIONAL
}
