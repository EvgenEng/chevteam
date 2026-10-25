package ru.chevteam.onestep.enrichment;

import java.time.Instant;
import java.util.UUID;

/**
 * Характеристика отеля, вычисленная пайплайном, — с источником и доказательством, а не догадка модели.
 *
 * @param code       машинный код: {@code SEA_DISTANCE}, {@code ROAD_NOISE}, {@code CUSTOM_PHARMACY}…
 * @param value      значение для человека: «420 м, 6 мин пешком», «тихо»
 * @param level      нормированное значение для сравнения: метры, число мест или уровень LOW / MEDIUM / HIGH
 * @param confidence 0..1 — насколько полны данные, на которых построен вывод
 * @param method     как посчитано: «ближайшая береговая линия OSM, по прямой × 1,25»
 * @param evidence   на чём основан вывод: «ул. Кирова (primary) — 35 м»
 */
public record HotelFact(
        UUID hotelId,
        String code,
        String title,
        String value,
        String level,
        Double number,
        double confidence,
        FactSource source,
        String method,
        String evidence,
        Instant computedAt) {
}
