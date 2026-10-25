package ru.chevteam.onestep.enrichment.internal.tools;

/**
 * Результат инструмента до сохранения.
 *
 * @param level нормированное значение: уровень (LOW / MEDIUM / HIGH, YES / NO…) для сравнения и сверки
 */
public record Computed(String value, String level, Double number, double confidence, String method, String evidence) {

    public Computed {
        confidence = Math.max(0, Math.min(1, confidence));
    }
}
