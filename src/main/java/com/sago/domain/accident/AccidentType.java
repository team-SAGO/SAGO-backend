package com.sago.domain.accident;

/**
 * 사고 유형. Step 2(사고 유형 및 부상 여부 확인)에서 선택되며 이후 대응 절차 분기의 기준이 된다.
 */
public enum AccidentType {
    PERSONAL,      // 대인사고
    VEHICLE,       // 차량 간 사고
    SINGLE,        // 단독 사고
    HIT_AND_RUN    // 뺑소니 사고
}
