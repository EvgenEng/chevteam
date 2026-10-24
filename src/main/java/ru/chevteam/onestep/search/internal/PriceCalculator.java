package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.search.PriceQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Цена за проживание: базовый тариф × сезонный коэффициент, округление до 10 ₽ за ночь.
 * Деньги — только {@link BigDecimal}.
 */
final class PriceCalculator {

    private static final BigDecimal TEN = BigDecimal.TEN;

    private PriceCalculator() {
    }

    static PriceQuote quote(BigDecimal basePricePerNight, BigDecimal seasonCoefficient, int nights) {
        if (nights < 1) {
            throw new IllegalArgumentException("nights must be positive: " + nights);
        }
        BigDecimal coefficient = seasonCoefficient == null ? BigDecimal.ONE : seasonCoefficient;
        BigDecimal perNight = basePricePerNight.multiply(coefficient)
                .divide(TEN, 0, RoundingMode.HALF_UP)
                .multiply(TEN);
        BigDecimal total = perNight.multiply(BigDecimal.valueOf(nights));
        return new PriceQuote(total, perNight, nights, "RUB");
    }
}
