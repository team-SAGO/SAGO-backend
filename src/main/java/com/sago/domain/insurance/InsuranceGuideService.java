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
        properties.getSteps().forEach(InsuranceGuideService::validate);

        this.steps = properties.getSteps().stream()
            .sorted(Comparator.comparingInt(InsuranceGuideStep::getOrder))
            .toList();
    }

    /**
     * 단계마다 실제 문구가 들어 있는지 확인한다.
     * yml 키를 잘못 적으면 값이 null인 채로 바인딩되므로, 목록이 비었는지만 봐서는
     * 제목 없는 안내가 그대로 배포될 수 있다.
     */
    private static void validate(InsuranceGuideStep step) {
        if (isBlank(step.getTitle())) {
            throw new IllegalStateException(
                "보험 절차 안내 " + step.getOrder() + "단계의 title이 비어 있습니다. "
                    + "insurance-guide.yml을 확인하세요.");
        }
        if (isBlank(step.getDescription())) {
            throw new IllegalStateException(
                "보험 절차 안내 " + step.getOrder() + "단계(" + step.getTitle()
                    + ")의 description이 비어 있습니다. insurance-guide.yml을 확인하세요.");
        }
        if (step.getTips().stream().anyMatch(InsuranceGuideService::isBlank)) {
            throw new IllegalStateException(
                "보험 절차 안내 " + step.getOrder() + "단계(" + step.getTitle()
                    + ")의 tips에 빈 항목이 있습니다. insurance-guide.yml을 확인하세요.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 전체 절차를 순서대로 돌려준다.
     */
    public List<InsuranceGuideStep> getSteps() {
        return steps;
    }
}
