package com.sago.domain.supplementquestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplementQuestionRepository extends JpaRepository<SupplementQuestion, Long> {

    List<SupplementQuestion> findByAccident_AccidentIdOrderByCreatedAtAsc(Long accidentId);
}
