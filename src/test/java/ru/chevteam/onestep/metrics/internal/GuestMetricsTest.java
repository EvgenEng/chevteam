package ru.chevteam.onestep.metrics.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.metrics.GuestEventType;
import ru.chevteam.onestep.metrics.TimeToChoice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.chevteam.onestep.metrics.GuestEventType.BOOKED;
import static ru.chevteam.onestep.metrics.GuestEventType.CHOOSE;
import static ru.chevteam.onestep.metrics.GuestEventType.COMPARE;
import static ru.chevteam.onestep.metrics.GuestEventType.SEARCH;

/** «Время до выбора»: от первого запроса до первого «Забронировать». */
class GuestMetricsTest {

    static final Instant T0 = Instant.parse("2026-10-25T10:00:00Z");

    static GuestMetrics.Event ev(UUID s, GuestEventType type, int second, Integer rank) {
        return new GuestMetrics.Event(s, type, T0.plusSeconds(second), rank);
    }

    @Test
    void timeIsFromFirstQueryToFirstChoice() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<GuestMetrics.Event> events = new ArrayList<>(List.of(
                // выбрал с первого запроса за 40 с, второй отель, оплатил
                ev(a, SEARCH, 0, null), ev(a, CHOOSE, 40, 2), ev(a, BOOKED, 90, null), ev(a, CHOOSE, 200, 5),
                // уточнял запрос, сравнивал, выбрал пятый за 2 мин
                ev(b, SEARCH, 0, null), ev(b, SEARCH, 30, null), ev(b, COMPARE, 60, null), ev(b, CHOOSE, 120, 5),
                // поискал и ушёл
                ev(c, SEARCH, 0, null)));
        TimeToChoice t = GuestMetrics.summarize(events);

        assertThat(t.sessions()).isEqualTo(3);
        assertThat(t.chose()).isEqualTo(2);
        assertThat(t.booked()).isEqualTo(1);
        assertThat(t.medianSeconds()).isEqualTo(80.0);
        assertThat(t.medianQueries()).isEqualTo(1.5);
        assertThat(t.fromTop3()).isEqualTo(0.5);
        assertThat(t.compared()).isEqualTo(0.5);
        assertThat(t.recent()).hasSize(3);
    }

    @Test
    void choiceWithoutSearchOrAfterHoursIsNotCounted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TimeToChoice t = GuestMetrics.summarize(new ArrayList<>(List.of(
                ev(a, CHOOSE, 0, 1),
                ev(b, SEARCH, 0, null), ev(b, CHOOSE, 3 * 3600, 1))));
        assertThat(t.sessions()).isEqualTo(1);
        assertThat(t.chose()).isZero();
        assertThat(t.medianSeconds()).isNull();
        assertThat(t.fromTop3()).isNull();
    }
}
