package ru.chevteam.onestep.reviews;

import java.util.List;

/**
 * @param total всего отзывов под фильтром
 * @param demo  отзывы демонстрационные: каталог прототипа вымышленный
 */
public record ReviewPage(ReviewSummary summary, List<Review> items, int total, int limit, int offset, boolean demo) {

    public ReviewPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
