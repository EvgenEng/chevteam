package ru.chevteam.onestep.shared.error;

/** Состояние ресурса не позволяет выполнить операцию (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
