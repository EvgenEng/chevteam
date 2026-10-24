package ru.chevteam.onestep.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookingView(
        UUID id,
        String reference,
        BookingStatus status,
        UUID hotelId,
        String hotelName,
        String city,
        String roomName,
        String mealPlan,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int adults,
        List<Integer> childrenAges,
        String guestName,
        BigDecimal total,
        String currency,
        boolean refundable,
        Instant holdExpiresAt,
        String lastPaymentError) {
}
