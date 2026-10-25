package ru.chevteam.onestep.enrichment.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Заявка агента данных: «для характеристики нужны объекты OSM, которых нет в снимке — скачать?».
 * Ничего не скачивается без решения администратора.
 *
 * @param filter   каких объектов не хватает, в синтаксисе фильтра: {@code leisure=playground}
 * @param query    точный запрос к Overpass API, который будет выполнен после одобрения
 * @param reason   объяснение агента: зачем и что будет без этих данных
 * @param elements сколько объектов скачано (после одобрения)
 */
public record SourceRequest(UUID id, String characteristicCode, String filter, String query, String reason, Status status,
                            Instant createdAt, Instant decidedAt, String decidedBy, Integer elements, String message) {

    public enum Status {
        /** Ждёт решения администратора. */
        PENDING,
        /** Одобрено и скачано — объекты уже в расчётах. */
        FETCHED,
        /** Одобрено, но серверы OSM не ответили — можно повторить. */
        FAILED,
        /** Отклонено: характеристика без данных не считается. */
        REJECTED
    }
}
