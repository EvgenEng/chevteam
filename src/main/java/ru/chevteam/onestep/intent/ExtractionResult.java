package ru.chevteam.onestep.intent;

import java.util.List;
import java.util.Objects;

/**
 * @param assumptions    допущения, которые система сделала сама («не указано число гостей — считаем 2 взрослых»)
 * @param clarifications вопросы пользователю, ответ на которые улучшит подбор
 */
public record ExtractionResult(
        SearchIntent intent,
        IntentSource source,
        List<String> assumptions,
        List<String> clarifications) {

    public ExtractionResult {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(source, "source");
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
    }

    public ExtractionResult withSource(IntentSource newSource, String extraAssumption) {
        List<String> merged = new java.util.ArrayList<>(assumptions);
        if (extraAssumption != null) {
            merged.add(0, extraAssumption);
        }
        return new ExtractionResult(intent, newSource, merged, clarifications);
    }
}
