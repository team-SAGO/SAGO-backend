package com.sago.global.client.gemini;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gemini generateContent API 호출 클라이언트.
 * 프롬프트 텍스트를 넣으면 응답 텍스트만 뽑아서 돌려준다.
 * 사용량 추적·재시도·폴백은 이 클라이언트가 아니라 호출하는 기능별 서비스에서 처리한다.
 */
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private final GeminiProperties properties;
    private final RestClient defaultRestClient;

    public GeminiClient(GeminiProperties properties) {
        this.properties = properties;
        this.defaultRestClient = buildRestClient(DEFAULT_READ_TIMEOUT);
    }

    public String generateContent(String prompt) {
        return generateContent(prompt, defaultRestClient);
    }

    /**
     * 5초 내 판단이 필요한 기능처럼, 기본 30초보다 짧은 응답 타임아웃이 필요할 때 사용한다.
     * 지정한 시간 안에 HTTP 응답이 오지 않으면 GeminiApiException으로 즉시 실패한다.
     */
    public String generateContent(String prompt, Duration readTimeout) {
        return generateContent(prompt, buildRestClient(readTimeout));
    }

    private RestClient buildRestClient(Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(requestFactory)
            .build();
    }

    private String generateContent(String prompt, RestClient restClient) {
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

        String text = content.parts().stream()
            .map(GeminiPart::text)
            .filter(part -> part != null && !part.isEmpty())
            .collect(Collectors.joining());

        if (text.isEmpty()) {
            throw new GeminiApiException("Gemini API 응답에 텍스트가 없습니다");
        }

        return text;
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
