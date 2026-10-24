package ru.chevteam.onestep.intent.llm;

/**
 * Порт к языковой модели. Реализации — адаптеры конкретных провайдеров.
 */
public interface LlmClient {

    /** Имя провайдера для логов и метрик. */
    String provider();

    /**
     * @return текст ответа модели
     * @throws LlmException при любой ошибке провайдера (сеть, авторизация, пустой ответ)
     */
    String complete(String systemPrompt, String userMessage);
}
