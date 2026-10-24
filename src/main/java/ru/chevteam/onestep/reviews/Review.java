package ru.chevteam.onestep.reviews;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param stayedOn первый день месяца поездки — точная дата не нужна, а сезон важен
 * @param tags     аспекты для выжимки: «+пляж», «-шум»
 */
public record Review(
        UUID id,
        String author,
        TripType tripType,
        BigDecimal rating,
        LocalDate stayedOn,
        String title,
        String pros,
        String cons,
        List<String> tags,
        OffsetDateTime createdAt) {

    public Review {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
