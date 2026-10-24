package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.catalog.Pricing;
import ru.chevteam.onestep.search.PriceQuote;

import java.math.BigDecimal;

/**
 * Быстрая оценка цены для выдачи: правило ночи — {@link Pricing}, коэффициент — месяца заезда.
 * Точная цена по каждой ночи считается при бронировании ({@code HotelCatalog.quote}).
 * Деньги — только {@link BigDecimal}.
 */
final class PriceCalculator {

    private PriceCalculator() {
    }

    static PriceQuote quote(BigDecimal basePricePerNight, BigDecimal seasonCoefficient, int nights) {
        if (nights < 1) {
            throw new IllegalArgumentException("nights must be positive: " + nights);
        }
        BigDecimal perNight = Pricing.nightPrice(basePricePerNight, seasonCoefficient);
        BigDecimal total = perNight.multiply(BigDecimal.valueOf(nights));
        return new PriceQuote(total, perNight, nights, "RUB", null, null);
    }
}
