package ru.chevteam.onestep.booking.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Фоновые задачи бронирования.
 */
@Component
class BookingJobs {

    private static final Logger log = LoggerFactory.getLogger(BookingJobs.class);
    private static final int BATCH = 100;

    private final BookingService bookings;
    private final IncompleteEventPublications incompletePublications;

    BookingJobs(BookingService bookings, IncompleteEventPublications incompletePublications) {
        this.bookings = bookings;
        this.incompletePublications = incompletePublications;
    }

    /** Возвращает в продажу номера, за которые не заплатили вовремя. */
    @Scheduled(fixedDelayString = "${app.booking.expiry-check-interval:PT30S}")
    void expireStaleHolds() {
        int processed;
        do {
            processed = bookings.expireStaleHolds(BATCH);
        } while (processed == BATCH);
    }

    /**
     * Outbox: события, которые не удалось доставить (например, Kafka была недоступна), переотправляются.
     * Само событие уже лежит в БД вместе с бронью — потерять его нельзя.
     */
    @Scheduled(fixedDelayString = "${app.booking.outbox-retry-interval:PT1M}")
    void resubmitUndeliveredEvents() {
        try {
            incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
        } catch (RuntimeException e) {
            log.warn("Outbox resubmission failed: {}", e.toString());
        }
    }
}
