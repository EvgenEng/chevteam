package ru.chevteam.onestep.booking.internal;

import ru.chevteam.onestep.shared.error.ConflictException;

class IllegalBookingStateException extends ConflictException {

    IllegalBookingStateException(String message) {
        super(message);
    }
}
