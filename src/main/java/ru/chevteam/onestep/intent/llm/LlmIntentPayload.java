package ru.chevteam.onestep.intent.llm;

import java.util.List;

/**
 * Сырой ответ LLM. Все поля намеренно «слабо типизированы» (строки, Integer):
 * модель может ошибиться, и валидация выполняется отдельно, по каждому полю.
 */
public record LlmIntentPayload(
        String country,
        String city,
        Integer month,
        String checkIn,
        Integer nights,
        Integer adults,
        List<Integer> childrenAges,
        Long budget,
        String budgetScope,
        Integer minStars,
        String mealPlan,
        List<String> amenities,
        List<String> softPreferences,
        List<String> locationFeatures,
        List<Landmark> landmarks,
        String originCity,
        Boolean directFlightPreferred) {

    public record Landmark(String name, Integer maxWalkMinutes) {
    }
}
