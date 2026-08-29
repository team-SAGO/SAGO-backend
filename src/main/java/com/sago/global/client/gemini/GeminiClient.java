package com.sago.global.client.gemini;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Gemini generateContent API 호출 클라이언트.
 * 프롬프트 텍스트를 넣으면 응답 텍스트만 뽑아서 돌려준다.
 * 사용량 추적·재시도·폴백은 이 클라이언트가 아니라 호출하는 기능별 서비스에서 처리한다.
 */
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(GeminiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(BASE_URL)
            .build();
    }

    public String generateContent(String prompt) {
        GeminiRequest request = new GeminiRequest(
            List.of(new GeminiContent(List.of(new GeminiPart(prompt))))
        );

        GeminiResponse response;
        try {
            response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/models/{model}:generateContent")
                    .queryParam("key", properties.getApiKey())
                    .build(properties.getModel()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);
        } catch (RestClientException e) {
            throw new GeminiApiException("Gemini API 호출 실패", e);
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new GeminiApiException("Gemini API 응답에 candidates가 없습니다");
        }

        GeminiContent content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new GeminiApiException("Gemini API 응답에 텍스트가 없습니다");
        }

        return content.parts().get(0).text();
    }

    private record GeminiRequest(List<GeminiContent> contents) {
    }

    private record GeminiResponse(List<GeminiCandidate> candidates) {
    }

    private record GeminiCandidate(GeminiContent content) {
    }

    private record GeminiContent(List<GeminiPart> parts) {
    }

    private record GeminiPart(String text) {
    }
}
