package ru.chevteam.onestep.intent.rules;

/**
 * Пояснение к разбору, привязанное к полю: допущение или уточняющий вопрос.
 * Привязка нужна, чтобы снять пояснение, если поле позже уточнил LLM.
 */
public record Note(IntentField field, String text) {
}
