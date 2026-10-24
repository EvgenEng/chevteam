package ru.chevteam.onestep.reviews;

import java.util.UUID;

/** Публичный API модуля отзывов. */
public interface Reviews {

    enum Subject { HOTEL, BEACH }

    /**
     * @param tripType {@code null} — все отзывы
     */
    ReviewPage find(Subject subject, UUID subjectId, TripType tripType, ReviewSort sort, int limit, int offset);
}
