package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.MealPlan;

import java.util.UUID;

/**
 * Жёсткие фильтры, которые выгодно выполнить в БД (индексы, PostGIS).
 * Всё «мягкое» оценивается уже в Java.
 */
record CandidateQuery(
        String country,
        String city,
        Integer minStars,
        int adults,
        int children,
        MealPlan mealPlan,
        int month,
        UUID landmarkId,
        Integer radiusMeters,
        int limit) {
}
