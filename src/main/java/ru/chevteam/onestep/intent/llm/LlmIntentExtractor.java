package ru.chevteam.onestep.intent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Извлекает намерение с помощью LLM: промпт → ответ → JSON → {@link LlmIntentPayload}.
 */
public final class LlmIntentExtractor {

    private final LlmClient client;
    private final ObjectReader reader;
    private final Clock clock;

    public LlmIntentExtractor(LlmClient client, ObjectMapper objectMapper, Clock clock) {
        this.client = client;
        this.reader = objectMapper.readerFor(LlmIntentPayload.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.clock = clock;
    }

    public String provider() {
        return client.provider();
    }

    public LlmIntentPayload extract(String query) {
        String raw = client.complete(IntentPrompt.system(LocalDate.now(clock)), query);
        String json = JsonBlock.extract(raw);
        try {
            return reader.readValue(json);
        } catch (JsonProcessingException e) {
            throw new LlmException("LLM returned malformed JSON", e);
        }
    }
}
