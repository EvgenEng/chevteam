package ru.chevteam.onestep.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Авторитетная цена тарифа на конкретные даты — считается на сервере, клиенту не доверяем.
 */
public record RateQuote(
        UUID rateId,
        UUID hotelId,
        String hotelName,
        String city,
        String roomName,
        String mealPlan,
        int maxAdults,
        int maxChildren,
        boolean refundable,
        int nights,
        BigDecimal total,
        String currency) {
}
