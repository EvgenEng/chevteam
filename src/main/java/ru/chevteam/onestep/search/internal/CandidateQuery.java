package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.MealPlan;

import java.util.UUID;

/**
 * Жёсткие фильтры, которые выгодно выполнить в БД (индексы, PostGIS).
 * Всё «мягкое» оценивается уже в Java.
 */
record CandidateQuery(
        String country,
        String region,
        String city,
        Integer minStars,
        int adults,
        int children,
        MealPlan mealPlan,
        int month,
        UUID landmarkId,
        Integer radiusMeters,
        int limit) {

    /** Тот же запрос в другом направлении. Достопримечательность привязана к городу, поэтому снимается. */
    CandidateQuery withDestination(String newCountry, String newRegion, String newCity) {
        return new CandidateQuery(newCountry, newRegion, newCity, minStars, adults, children, mealPlan, month,
                null, null, limit);
    }
}
