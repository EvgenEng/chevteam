package ru.chevteam.onestep.booking.web;

import jakarta.validation.constraints.NotNull;
import ru.chevteam.onestep.booking.PaymentMethod;

public record PayRequest(@NotNull PaymentMethod method) {
}
