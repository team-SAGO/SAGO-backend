package com.sago.domain.statement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByAccident_AccidentIdOrderByCreatedAtAsc(Long accidentId);
}
