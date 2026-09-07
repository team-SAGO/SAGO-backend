package com.sago.domain.terms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "terms")
public class TermsProperties {

    private List<TermsDocument> documents = new ArrayList<>();

    public List<TermsDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<TermsDocument> documents) {
        this.documents = documents == null ? new ArrayList<>() : documents;
    }
}
