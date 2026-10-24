package ru.chevteam.onestep.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingTest {

    @Test
    void nightPriceIsRoundedToTenRoubles() {
        assertThat(Pricing.nightPrice(new BigDecimal("6900"), new BigDecimal("1.15"))).isEqualByComparingTo("7940");
    }

    @Test
    void mealAddonCountsAdultsAndPayingChildren() {
        // 2 × 1 500 + 1 × 750 (ребёнку 8 лет), малыш 3 лет бесплатно; 4 ночи
        assertThat(Pricing.mealAddonTotal(new BigDecimal("1500"), new BigDecimal("750"), 4, 2, List.of(3, 8), 4))
                .isEqualByComparingTo("15000");
        assertThat(Pricing.mealAddonTotal(new BigDecimal("1500"), new BigDecimal("750"), 4, 1, null, 1))
                .isEqualByComparingTo("1500");
    }

    @Test
    void mealAddonRejectsEmptyStay() {
        assertThatThrownBy(() -> Pricing.mealAddonTotal(BigDecimal.ONE, BigDecimal.ONE, 4, 1, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
