package ru.chevteam.onestep.search.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.search.PriceQuote;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    @Test
    void appliesSeasonAndRoundsToTenRubles() {
        PriceQuote quote = PriceCalculator.quote(new BigDecimal("6900"), new BigDecimal("1.15"), 6);
        assertThat(quote.perNight()).isEqualByComparingTo("7940"); // 7935 → 7940
        assertThat(quote.total()).isEqualByComparingTo("47640");
        assertThat(quote.currency()).isEqualTo("RUB");
    }

    @Test
    void missingCoefficientMeansBaseSeason() {
        assertThat(PriceCalculator.quote(new BigDecimal("4200"), null, 2).total()).isEqualByComparingTo("8400");
    }

    @Test
    void rejectsZeroNights() {
        assertThatThrownBy(() -> PriceCalculator.quote(BigDecimal.TEN, BigDecimal.ONE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
