package ru.chevteam.onestep.booking.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HoldCommand(
        UUID rateId,
        LocalDate checkIn,
        int nights,
        int adults,
        List<Integer> childrenAges,
        String guestName,
        String guestEmail) {

    public HoldCommand {
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
    }
}
