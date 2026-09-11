package com.sago.domain.contact.dto;

import com.sago.domain.contact.ContactLog;
import com.sago.domain.contact.ContactType;

import java.time.LocalDateTime;

public record ContactLogResponse(Long contactId, ContactType contactType, LocalDateTime contactedAt) {

    public static ContactLogResponse from(ContactLog contactLog) {
        return new ContactLogResponse(
            contactLog.getContactId(),
            contactLog.getContactType(),
            contactLog.getContactedAt()
        );
    }
}
