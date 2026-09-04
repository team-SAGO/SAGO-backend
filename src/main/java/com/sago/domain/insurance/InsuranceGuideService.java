package com.sago.domain.insurance;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 사고 후 보험 처리 절차 안내를 제공한다.
 *
 * 안내 문구는 사용자마다 달라지지 않는 정적 콘텐츠라 DB에 두지 않고 설정 파일에서 읽는다.
 * 사고 접수·과실 협의처럼 순서가 의미를 갖는 내용이므로 order 기준으로 정렬해 돌려준다.
 */
@Service
public class InsuranceGuideService {

    private final List<InsuranceGuideStep> steps;

    public InsuranceGuideService(InsuranceGuideProperties properties) {
        if (properties.getSteps().isEmpty()) {
            throw new IllegalStateException(
                "보험 절차 안내 문구가 비어 있습니다. insurance-guide.yml을 확인하세요.");
        }
        this.steps = properties.getSteps().stream()
            .sorted(Comparator.comparingInt(InsuranceGuideStep::getOrder))
            .toList();
    }

    /**
     * 전체 절차를 순서대로 돌려준다.
     */
    public List<InsuranceGuideStep> getSteps() {
        return steps;
    }
}
