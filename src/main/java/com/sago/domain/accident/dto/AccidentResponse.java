package com.sago.domain.accident.dto;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentStatus;
import com.sago.domain.accident.AccidentType;
import com.sago.domain.accident.InjuryLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccidentResponse(
    Long accidentId,
    AccidentType accidentType,
    InjuryLevel injurySelf,
    InjuryLevel injuryOther,
    LocalDateTime occurredAt,
    BigDecimal latitude,
    BigDecimal longitude,
    String direction,
    String roadCondition,
    String memo,
    AccidentStatus status,
    LocalDateTime createdAt
) {

    public static AccidentResponse from(Accident accident) {
        return new AccidentResponse(
            accident.getAccidentId(),
            accident.getAccidentType(),
            accident.getInjurySelf(),
            accident.getInjuryOther(),
            accident.getOccurredAt(),
            accident.getLatitude(),
            accident.getLongitude(),
            accident.getDirection(),
            accident.getRoadCondition(),
            accident.getMemo(),
            accident.getStatus(),
            accident.getCreatedAt()
        );
    }
}
