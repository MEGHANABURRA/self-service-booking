package com.phenom.selfservicebooking.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

@Component
class GoogleCloudTranslationClient implements PhraseTranslator {

    private final RestClient restClient;
    private final String apiKey;
    private final Map<CacheKey, String> translations = new ConcurrentHashMap<>();

    GoogleCloudTranslationClient(
            RestClient.Builder restClientBuilder,
            @Value("${google.cloud.translation.api-key:}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl("https://translation.googleapis.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public String translate(String phrase, Locale targetLocale) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_TRANSLATE_API_KEY is not configured");
        }

        String languageCode = targetLocale.getLanguage();
        if (languageCode.isBlank()) {
            throw new IllegalArgumentException("Target locale must contain a language");
        }

        CacheKey cacheKey = new CacheKey(languageCode, phrase);
        return translations.computeIfAbsent(cacheKey, this::requestTranslation);
    }

    private String requestTranslation(CacheKey cacheKey) {
        JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/language/translate/v2")
                        .queryParam("key", apiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "q", cacheKey.phrase(),
                        "source", "en",
                        "target", cacheKey.languageCode(),
                        "format", "text"))
                .retrieve()
                .body(JsonNode.class);

        JsonNode translatedText = response == null
                ? null
                : response.path("data").path("translations").path(0).path("translatedText");
        if (translatedText == null || !translatedText.isTextual()
                || translatedText.asText().isBlank()) {
            throw new IllegalStateException("Google Cloud Translation returned no translated text");
        }
        return HtmlUtils.htmlUnescape(translatedText.asText());
    }

    private record CacheKey(String languageCode, String phrase) {
    }
}
