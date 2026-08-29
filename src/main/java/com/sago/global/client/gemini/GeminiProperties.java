package com.sago.global.client.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey;
    private String model;
    private int dailyRequestLimit;
    private int rpmLimit;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDailyRequestLimit() {
        return dailyRequestLimit;
    }

    public void setDailyRequestLimit(int dailyRequestLimit) {
        this.dailyRequestLimit = dailyRequestLimit;
    }

    public int getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(int rpmLimit) {
        this.rpmLimit = rpmLimit;
    }
}
