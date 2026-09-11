package com.sago.domain.contact.dto;

import com.sago.domain.contact.ContactType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * @param contactedAt 버튼을 누른 시각. 보내지 않으면 서버 시각으로 기록한다.
 *                    통신이 끊겼다가 나중에 전송하는 경우 반드시 보내야 실제 시각이 남는다.
 */
public record ContactLogCreateRequest(
    @NotNull(message = "연결 유형은 필수입니다.") ContactType contactType,
    LocalDateTime contactedAt
) {
}
