package com.sago.domain.contact;

import com.sago.domain.contact.dto.ContactLogCreateRequest;
import com.sago.domain.contact.dto.ContactLogResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 신고·보험사 연결 기록 (FR-07). 사고 하위 리소스다. */
@RestController
@RequestMapping("/api/accidents/{accidentId}/contacts")
public class ContactLogController {

    private final ContactLogService contactLogService;

    public ContactLogController(ContactLogService contactLogService) {
        this.contactLogService = contactLogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactLogResponse record(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long accidentId,
                                     @Valid @RequestBody ContactLogCreateRequest request) {
        return contactLogService.record(userId, accidentId, request);
    }

    @GetMapping
    public List<ContactLogResponse> getContactLogs(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long accidentId) {
        return contactLogService.getContactLogs(userId, accidentId);
    }
}
