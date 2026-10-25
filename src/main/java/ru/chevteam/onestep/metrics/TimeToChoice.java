package ru.chevteam.onestep.metrics;

import java.time.Instant;
import java.util.List;

/**
 * Сводка для админки.
 *
 * @param sessions          сессий, где был хотя бы один поиск
 * @param chose             из них дошли до «Забронировать»
 * @param booked            из них оплатили бронь
 * @param medianSeconds     медиана времени от первого запроса до выбора, с; {@code null} — выборов ещё не было
 * @param medianQueries     медиана числа запросов до выбора (1 — выбрали с первого запроса)
 * @param fromTop3          доля выборов из первых трёх отелей выдачи, 0..1
 * @param compared          доля выборов, перед которыми гость открывал сравнение, 0..1
 * @param recent            последние сессии — для живой демонстрации
 */
public record TimeToChoice(int sessions, int chose, int booked, Double medianSeconds, Double medianQueries, Double fromTop3,
                           Double compared, List<Session> recent) {

    /**
     * @param seconds до выбора, с; {@code null} — выбора не было
     * @param rank    место выбранного отеля в выдаче
     */
    public record Session(Instant startedAt, int queries, Long seconds, Integer rank, boolean compared, boolean booked) {
    }
}
