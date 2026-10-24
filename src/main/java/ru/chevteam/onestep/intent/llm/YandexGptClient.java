package ru.chevteam.onestep.intent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Адаптер YandexGPT (Yandex Cloud Foundation Models, синхронный режим completion).
 */
public final class YandexGptClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties.YandexGpt props;

    public YandexGptClient(RestClient.Builder builder, LlmProperties.YandexGpt props) {
        this.props = Objects.requireNonNull(props, "yandexgpt properties");
        if (isBlank(props.apiKey()) || isBlank(props.folderId())) {
            throw new IllegalStateException(
                    "app.llm.yandexgpt.api-key and folder-id are required when provider=yandexgpt");
        }
        this.restClient = builder.build();
    }

    @Override
    public String provider() {
        return "yandexgpt";
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        Map<String, Object> request = Map.of(
                "modelUri", "gpt://" + props.folderId() + "/" + props.model() + "/latest",
                "completionOptions", Map.of("stream", false, "temperature", 0.1, "maxTokens", "800"),
                "messages", List.of(
                        Map.of("role", "system", "text", systemPrompt),
                        Map.of("role", "user", "text", userMessage)));
        try {
            JsonNode response = restClient.post()
                    .uri(props.apiUrl() + "/completion")
                    .header(HttpHeaders.AUTHORIZATION, "Api-Key " + props.apiKey())
                    .header("x-folder-id", props.folderId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? null
                    : response.path("result").path("alternatives").path(0).path("message").path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new LlmException("YandexGPT returned empty completion");
            }
            return text;
        } catch (RestClientResponseException e) {
            throw new LlmException("YandexGPT responded " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new LlmException("YandexGPT call failed", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
