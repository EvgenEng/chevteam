package ru.chevteam.onestep.intent.llm;

/**
 * Модели иногда оборачивают JSON в ```json ... ``` или добавляют фразу до/после.
 * Берём первый сбалансированный JSON-объект.
 */
final class JsonBlock {

    private JsonBlock() {
    }

    static String extract(String raw) {
        if (raw == null) {
            throw new LlmException("Empty LLM response");
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            throw new LlmException("No JSON object in LLM response");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return raw.substring(start, i + 1);
            }
        }
        throw new LlmException("Unbalanced JSON in LLM response");
    }
}
