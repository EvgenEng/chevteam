package ru.chevteam.onestep.intent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        Provider provider,
        Duration connectTimeout,
        Duration readTimeout,
        GigaChat gigachat,
        YandexGpt yandexgpt) {

    public enum Provider { NONE, GIGACHAT, YANDEXGPT }

    public LlmProperties {
        provider = provider == null ? Provider.NONE : provider;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    public record GigaChat(String authKey, String scope, String model, String oauthUrl, String apiUrl) {
    }

    public record YandexGpt(String apiKey, String folderId, String model, String apiUrl) {
    }
}
