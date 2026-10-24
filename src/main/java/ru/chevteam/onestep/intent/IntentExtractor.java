package ru.chevteam.onestep.intent;

/**
 * Порт «понимания» запроса.
 */
public interface IntentExtractor {

    ExtractionResult extract(String query);
}
