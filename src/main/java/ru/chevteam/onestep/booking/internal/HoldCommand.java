package ru.chevteam.onestep.booking.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param mealUpgrade питание, которое гость добавляет к тарифу (BREAKFAST, HALF_BOARD…), или {@code null}
 */
public record HoldCommand(
        UUID rateId,
        LocalDate checkIn,
        int nights,
        int adults,
        List<Integer> childrenAges,
        String guestName,
        String guestEmail,
        String mealUpgrade) {

    public HoldCommand {
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        mealUpgrade = mealUpgrade == null || mealUpgrade.isBlank() ? null : mealUpgrade.strip();
    }

    public HoldCommand(UUID rateId, LocalDate checkIn, int nights, int adults, List<Integer> childrenAges,
                       String guestName, String guestEmail) {
        this(rateId, checkIn, nights, adults, childrenAges, guestName, guestEmail, null);
    }
}
