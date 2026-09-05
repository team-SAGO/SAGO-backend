package com.sago.domain.accident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccidentRepository extends JpaRepository<Accident, Long> {

    List<Accident> findByUser_UserIdOrderByOccurredAtDesc(Long userId);
}
