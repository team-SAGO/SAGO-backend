package com.sago.domain.terms;

/**
 * terms.yml에 정의된 약관 하나. 사용자마다 달라지지 않는 정적 정보다.
 */
public class TermsDocument {

    private TermsType type;
    private boolean required;
    private String version;
    private String title;
    private String contentUrl;

    public TermsType getType() {
        return type;
    }

    public void setType(TermsType type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }
}
