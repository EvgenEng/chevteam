package ru.chevteam.onestep.intent;

public enum IntentSource {
    /** Разобрано LLM и провалидировано правилами. */
    AI,
    /** Разобрано детерминированными правилами (LLM выключен или недоступен). */
    RULES
}
