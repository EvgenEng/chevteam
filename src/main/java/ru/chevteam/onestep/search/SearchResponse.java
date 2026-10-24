package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.SearchIntent;

import java.util.List;

/**
 * @param understood как система поняла запрос — показывается пользователю «чипсами», чтобы он мог поправить
 */
public record SearchResponse(Understood understood, List<HotelMatch> hotels, int totalCandidates) {

    public record Understood(
            SearchIntent intent,
            IntentSource source,
            List<String> summary,
            List<String> assumptions,
            List<String> clarifications) {
    }
}
