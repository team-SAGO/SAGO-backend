package com.sago.domain.insurance;

import java.util.List;

/**
 * 사고 후 보험 처리 절차의 한 단계.
 *
 * 문구는 코드가 아니라 `insurance-guide.yml`에서 관리한다.
 * 기획·법률 검토로 표현이 바뀔 때 코드를 건드리지 않고 고칠 수 있어야 하기 때문이다.
 */
public class InsuranceGuideStep {

    private int order;
    private String title;
    private String description;
    private List<String> tips = List.of();

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTips() {
        return tips;
    }

    public void setTips(List<String> tips) {
        this.tips = tips == null ? List.of() : tips;
    }
}
