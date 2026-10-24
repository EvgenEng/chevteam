package ru.chevteam.onestep.search.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WalkingTimeTest {

    @ParameterizedTest
    @CsvSource({"0, 1", "64, 1", "433, 7", "1274, 20"})
    void convertsMetersToMinutes(int meters, int minutes) {
        assertThat(WalkingTime.minutes(meters)).isEqualTo(minutes);
    }

    @ParameterizedTest
    @CsvSource({"15, 960", "22.5, 1440"})
    void radiusIsInverseOfMinutes(double minutes, int radius) {
        assertThat(WalkingTime.radiusMeters(minutes)).isEqualTo(radius);
        assertThat(WalkingTime.minutes(radius)).isLessThanOrEqualTo((int) Math.ceil(minutes));
    }
}
