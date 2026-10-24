package ru.chevteam.onestep.booking;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.UUID;

/** Удержание истекло без оплаты, номер вернулся в продажу. */
@Externalized(BookingEvents.TOPIC + "::#{reference()}")
public record BookingExpired(UUID bookingId, String reference, UUID hotelId, Instant occurredAt) {
}
