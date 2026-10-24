package ru.chevteam.onestep.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Единое правило цены ночи для поиска и бронирования: базовый тариф × сезонный коэффициент,
 * округление до 10 ₽. Если правило поменяется, оно поменяется везде одновременно.
 */
public final class Pricing {

    private static final BigDecimal TEN = BigDecimal.TEN;

    private Pricing() {
    }

    public static BigDecimal nightPrice(BigDecimal basePricePerNight, BigDecimal seasonCoefficient) {
        BigDecimal coefficient = seasonCoefficient == null ? BigDecimal.ONE : seasonCoefficient;
        return basePricePerNight.multiply(coefficient)
                .divide(TEN, 0, RoundingMode.HALF_UP)
                .multiply(TEN);
    }
}
