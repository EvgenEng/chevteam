package ru.chevteam.onestep.booking;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Бронь оплачена. Ключ сообщения — номер брони: все события одной брони попадают в одну партицию
 * и читаются потребителями по порядку.
 */
@Externalized(BookingEvents.TOPIC + "::#{reference()}")
public record BookingConfirmed(
        UUID bookingId,
        String reference,
        UUID hotelId,
        String hotelName,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        int guests,
        BigDecimal total,
        String currency,
        Instant occurredAt) {
}
