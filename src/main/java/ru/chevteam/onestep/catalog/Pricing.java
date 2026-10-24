package ru.chevteam.onestep.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Единое правило цены ночи для поиска и бронирования: базовый тариф × сезонный коэффициент,
 * округление до 10 ₽. Если правило поменяется, оно поменяется везде одновременно.
 */
public final class Pricing {

    private static final BigDecimal TEN = BigDecimal.TEN;

    private Pricing() {
    }

    /**
     * Стоимость питания, добавленного к тарифу, за всю поездку: взрослые и дети не младше
     * {@code childFreeUnder} платят каждую ночь, малыши едят бесплатно. Сезонный коэффициент к питанию не применяется.
     */
    public static BigDecimal mealAddonTotal(BigDecimal adultPerNight, BigDecimal childPerNight, int childFreeUnder,
                                            int adults, List<Integer> childrenAges, int nights) {
        if (nights < 1 || adults < 1) {
            throw new IllegalArgumentException("nights and adults must be positive");
        }
        long payingChildren = childrenAges == null ? 0 : childrenAges.stream().filter(age -> age >= childFreeUnder).count();
        return adultPerNight.multiply(BigDecimal.valueOf(adults))
                .add(childPerNight.multiply(BigDecimal.valueOf(payingChildren)))
                .multiply(BigDecimal.valueOf(nights))
                .setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal nightPrice(BigDecimal basePricePerNight, BigDecimal seasonCoefficient) {
        BigDecimal coefficient = seasonCoefficient == null ? BigDecimal.ONE : seasonCoefficient;
        return basePricePerNight.multiply(coefficient)
                .divide(TEN, 0, RoundingMode.HALF_UP)
                .multiply(TEN);
    }
}
