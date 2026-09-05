package com.sago.domain.accident.dto;

import com.sago.domain.accident.AccidentType;
import com.sago.domain.accident.InjuryLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사고 기록 생성 요청.
 *
 * 사고 직후 경황이 없는 상태에서 눌리는 화면이라 필수 항목은 사고 유형 하나로 두었다.
 * 나머지는 이후 화면에서 채워 넣는다.
 *
 * @param occurredAt 발생 시각. 보내지 않으면 요청 시각으로 기록한다.
 * @param latitude   위도. 위치 권한을 거부한 경우 없을 수 있다.
 * @param longitude  경도.
 */
public record AccidentCreateRequest(

    @NotNull(message = "사고 유형은 필수입니다.")
    AccidentType accidentType,

    InjuryLevel injurySelf,

    InjuryLevel injuryOther,

    LocalDateTime occurredAt,

    BigDecimal latitude,

    BigDecimal longitude,

    @Size(max = 50, message = "진행 방향은 50자 이하여야 합니다.")
    String direction,

    @Size(max = 100, message = "도로 상태는 100자 이하여야 합니다.")
    String roadCondition,

    String memo
) {
}
