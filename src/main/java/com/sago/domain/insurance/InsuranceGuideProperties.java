package com.sago.domain.insurance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "insurance.guide")
public class InsuranceGuideProperties {

    private List<InsuranceGuideStep> steps = new ArrayList<>();

    public List<InsuranceGuideStep> getSteps() {
        return steps;
    }

    public void setSteps(List<InsuranceGuideStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }
}
