package com.sago.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 사고의 경위서. 사고당 한 건이라 Optional이다 (uk_report_accident).
     *
     * 별도 인덱스를 두지 않았다. accident_id의 유니크 제약이 곧 인덱스라 조회도 그것을 탄다.
     */
    Optional<Report> findByAccident_AccidentId(Long accidentId);
}
