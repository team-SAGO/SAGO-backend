package com.sago.domain.photo;

/**
 * Vision 태깅 결과의 종류. 파손 유형(긁힘·파손·찌그러짐 등)은 DAMAGE,
 * 번호판·신호등·도로 표지판 등 사물은 OBJECT로 분류한다.
 */
public enum TagType {
    DAMAGE,
    OBJECT
}
