package ru.chevteam.onestep.intent.llm;

import java.util.List;

/**
 * Сырой ответ LLM. Все поля намеренно «слабо типизированы» (строки, Integer):
 * модель может ошибиться, и валидация выполняется отдельно, по каждому полю.
 */
public record LlmIntentPayload(
        String country,
        String region,
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
        Boolean directFlightPreferred,
        List<String> beachPreferences,
        List<String> activities,
        List<String> accessNeeds,
        List<String> roomFeatures,
        List<String> pets,
        Boolean visaFreeOnly,
        List<String> wildlife,
        List<String> diet,
        List<Transit> transit) {

    public record Landmark(String name, Integer maxWalkMinutes) {
    }

    /** «До аэропорта не больше 40 минут». */
    public record Transit(String kind, Integer maxMinutes) {
    }
}
