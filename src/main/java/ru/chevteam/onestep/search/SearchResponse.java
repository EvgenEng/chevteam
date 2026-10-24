package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.SearchIntent;

import java.util.List;

/**
 * @param understood как система поняла запрос — показывается пользователю «чипсами», чтобы он мог поправить
 * @param notice     важное пояснение к выдаче (например, что в направлении нет отелей и показаны альтернативы)
 */
public record SearchResponse(Understood understood, List<HotelMatch> hotels, int totalCandidates, String notice) {

    public record Understood(
            SearchIntent intent,
            IntentSource source,
            List<String> summary,
            List<String> assumptions,
            List<String> clarifications) {
    }
}
