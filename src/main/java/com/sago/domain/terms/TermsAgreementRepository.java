package com.sago.domain.terms;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    /**
     * 회원의 약관 유형별 최신 동의 기록만 가져온다.
     *
     * 동의 이력은 덮어쓰지 않고 쌓이므로 한 유형에 여러 행이 있을 수 있고, 그중 마지막 것이
     * 현재 상태다. 같은 순간에 두 행이 들어가는 일은 없지만, agreedAt이 같아도 결과가
     * 흔들리지 않도록 agreementId까지 함께 비교한다.
     */
    @Query("""
        select a from TermsAgreement a
        where a.user.userId = :userId
          and a.agreementId = (
            select max(latest.agreementId) from TermsAgreement latest
            where latest.user.userId = :userId
              and latest.termsType = a.termsType
          )
        """)
    List<TermsAgreement> findLatestByUserId(@Param("userId") Long userId);
}
