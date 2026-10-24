package ru.chevteam.onestep.intent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Адаптер GigaChat API (Сбер).
 * <p>
 * Авторизация двухшаговая: ключ авторизации меняется на access token (живёт ~30 минут),
 * токен кэшируется и обновляется заранее; при 401 — принудительное обновление и один повтор.
 * <p>
 * Важно: сертификаты GigaChat выпущены НУЦ Минцифры — корневой сертификат нужно добавить
 * в truststore JVM (см. README).
 */
public final class GigaChatClient implements LlmClient {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final LlmProperties.GigaChat props;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile AccessToken token;

    public GigaChatClient(RestClient.Builder builder, LlmProperties.GigaChat props, Clock clock) {
        this.props = Objects.requireNonNull(props, "gigachat properties");
        if (props.authKey() == null || props.authKey().isBlank()) {
            throw new IllegalStateException("app.llm.gigachat.auth-key is required when provider=gigachat");
        }
        this.restClient = builder.build();
        this.clock = clock;
    }

    @Override
    public String provider() {
        return "gigachat";
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        try {
            return chat(systemPrompt, userMessage, currentToken(false));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                return chat(systemPrompt, userMessage, currentToken(true));
            }
            throw new LlmException("GigaChat responded " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new LlmException("GigaChat call failed", e);
        }
    }

    private String chat(String systemPrompt, String userMessage, String accessToken) {
        Map<String, Object> request = Map.of(
                "model", props.model(),
                "temperature", 0.1,
                "max_tokens", 800,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)));

        JsonNode response = restClient.post()
                .uri(props.apiUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        String content = response == null ? null
                : response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new LlmException("GigaChat returned empty completion");
        }
        return content;
    }

    private String currentToken(boolean forceRefresh) {
        AccessToken current = token;
        if (!forceRefresh && current != null && current.isValidAt(clock.instant().plus(REFRESH_MARGIN))) {
            return current.value();
        }
        refreshLock.lock();
        try {
            current = token;
            if (forceRefresh || current == null || !current.isValidAt(clock.instant().plus(REFRESH_MARGIN))) {
                token = current = requestToken();
            }
            return current.value();
        } finally {
            refreshLock.unlock();
        }
    }

    private AccessToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scope", props.scope());
        try {
            JsonNode response = restClient.post()
                    .uri(props.oauthUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + props.authKey())
                    .header("RqUID", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                throw new LlmException("GigaChat OAuth returned no access_token");
            }
            Instant expiresAt = response.hasNonNull("expires_at")
                    ? Instant.ofEpochMilli(response.get("expires_at").asLong())
                    : clock.instant().plus(Duration.ofMinutes(25));
            return new AccessToken(response.get("access_token").asText(), expiresAt);
        } catch (RestClientException e) {
            throw new LlmException("GigaChat OAuth failed", e);
        }
    }

    private record AccessToken(String value, Instant expiresAt) {
        boolean isValidAt(Instant moment) {
            return moment.isBefore(expiresAt);
        }
    }
}
