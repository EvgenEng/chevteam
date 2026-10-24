package ru.chevteam.onestep.reviews;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Выжимка по отзывам: что чаще всего хвалят и на что жалуются.
 *
 * @param byTripType сколько отзывов от каждого типа путешественников (без учёта фильтра — для кнопок фильтра)
 */
public record ReviewSummary(
        int count,
        BigDecimal average,
        Map<TripType, Integer> byTripType,
        List<Aspect> praised,
        List<Aspect> criticized) {

    public ReviewSummary {
        byTripType = byTripType == null ? Map.of() : Map.copyOf(byTripType);
        praised = praised == null ? List.of() : List.copyOf(praised);
        criticized = criticized == null ? List.of() : List.copyOf(criticized);
    }

    public record Aspect(String label, int mentions) {
    }
}
