package ru.chevteam.onestep.booking.internal;

import ru.chevteam.onestep.shared.error.ConflictException;

class SoldOutException extends ConflictException {

    SoldOutException(String message) {
        super(message);
    }
}
