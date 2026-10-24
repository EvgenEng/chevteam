package ru.chevteam.onestep.booking.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.chevteam.onestep.booking.internal.HoldCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HoldRequest(
        @NotNull UUID rateId,
        @NotNull LocalDate checkIn,
        @Min(1) @Max(30) int nights,
        @Min(1) @Max(10) int adults,
        @Size(max = 6) List<@NotNull @Min(0) @Max(17) Integer> childrenAges,
        @NotBlank @Size(max = 200) String guestName,
        @NotBlank @Email @Size(max = 200) String guestEmail) {

    HoldCommand toCommand() {
        return new HoldCommand(rateId, checkIn, nights, adults, childrenAges, guestName, guestEmail);
    }
}
