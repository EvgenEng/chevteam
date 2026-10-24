package ru.chevteam.onestep.booking;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Бронь отменена гостем. {@code wasConfirmed} — нужен ли возврат денег. */
@Externalized(BookingEvents.TOPIC + "::#{reference()}")
public record BookingCancelled(
        UUID bookingId,
        String reference,
        UUID hotelId,
        LocalDate checkIn,
        LocalDate checkOut,
        boolean wasConfirmed,
        Instant occurredAt) {
}
