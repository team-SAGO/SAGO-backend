package com.sago.domain.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByAccident_AccidentIdOrderByOrderNoAsc(Long accidentId);
}
