package ru.chevteam.onestep.intent.rules;

import ru.chevteam.onestep.intent.SearchIntent;

import java.util.List;
import java.util.Set;

/**
 * Полный результат разбора правилами.
 *
 * @param explicit       поля, найденные в тексте явно (им можно доверять больше, чем LLM)
 * @param assumptions    допущения (значения по умолчанию, интерпретации)
 * @param clarifications вопросы пользователю
 */
public record RuleAnalysis(
        SearchIntent intent,
        Set<IntentField> explicit,
        List<Note> assumptions,
        List<Note> clarifications) {

    public RuleAnalysis {
        explicit = Set.copyOf(explicit);
        assumptions = List.copyOf(assumptions);
        clarifications = List.copyOf(clarifications);
    }

    public boolean isExplicit(IntentField field) {
        return explicit.contains(field);
    }
}
