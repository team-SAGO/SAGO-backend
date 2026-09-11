package com.sago.domain.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactLogRepository extends JpaRepository<ContactLog, Long> {

    List<ContactLog> findByAccident_AccidentIdOrderByContactedAtAsc(Long accidentId);
}
