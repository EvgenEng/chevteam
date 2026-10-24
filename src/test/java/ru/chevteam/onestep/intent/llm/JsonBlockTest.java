package ru.chevteam.onestep.intent.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBlockTest {

    @Test
    void extractsFromMarkdownFence() {
        String raw = "Вот результат:\n```json\n{\"city\": \"Сочи\", \"nested\": {\"a\": 1}}\n```\nГотово!";
        assertThat(JsonBlock.extract(raw)).isEqualTo("{\"city\": \"Сочи\", \"nested\": {\"a\": 1}}");
    }

    @Test
    void ignoresBracesInsideStrings() {
        assertThat(JsonBlock.extract("{\"note\": \"скобка } внутри\"}")).isEqualTo("{\"note\": \"скобка } внутри\"}");
    }

    @Test
    void failsOnGarbage() {
        assertThatThrownBy(() -> JsonBlock.extract("извините, не могу помочь")).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> JsonBlock.extract("{\"a\": 1")).isInstanceOf(LlmException.class);
    }
}
