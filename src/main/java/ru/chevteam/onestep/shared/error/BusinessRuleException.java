package ru.chevteam.onestep.shared.error;

/** Запрос корректен синтаксически, но нарушает бизнес-правило (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
