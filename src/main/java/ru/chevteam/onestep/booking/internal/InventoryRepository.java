package ru.chevteam.onestep.booking.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Остатки номеров. Защита от овербукинга — условный UPDATE: строка увеличивается, только если
 * {@code booked < allotment}. Это атомарно на уровне строки, а READ COMMITTED перечитывает условие
 * после снятия блокировки конкурентом — поэтому две брони на последний номер не пройдут обе.
 * Дополнительная страховка — CHECK-ограничение {@code booked <= allotment} в схеме.
 */
@Repository
class InventoryRepository {

    private final JdbcClient jdbc;

    InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Резервирует номер на каждую ночь [checkIn, checkOut). Если хотя бы одна ночь занята —
     * исключение, и транзакция откатывает уже увеличенные счётчики.
     */
    void reserve(UUID rateId, LocalDate checkIn, LocalDate checkOut) {
        jdbc.sql("""
                        INSERT INTO room_inventory (rate_id, stay_date, allotment)
                        SELECT rr.id, CAST(d AS DATE), rr.allotment
                          FROM room_rate rr,
                               generate_series(CAST(:checkIn AS DATE), CAST(:lastNight AS DATE), INTERVAL '1 day') AS d
                         WHERE rr.id = :rateId
                        ON CONFLICT (rate_id, stay_date) DO NOTHING
                        """)
                .param("rateId", rateId)
                .param("checkIn", checkIn)
                .param("lastNight", checkOut.minusDays(1))
                .update();

        int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        int reserved = jdbc.sql("""
                        UPDATE room_inventory
                           SET booked = booked + 1
                         WHERE rate_id = :rateId
                           AND stay_date >= :checkIn AND stay_date < :checkOut
                           AND booked < allotment
                        """)
                .param("rateId", rateId)
                .param("checkIn", checkIn)
                .param("checkOut", checkOut)
                .update();

        if (reserved != nights) {
            throw new SoldOutException("На выбранные даты этот номер закончился. Попробуйте другие даты или номер.");
        }
    }

    void release(UUID rateId, LocalDate checkIn, LocalDate checkOut) {
        jdbc.sql("""
                        UPDATE room_inventory
                           SET booked = booked - 1
                         WHERE rate_id = :rateId
                           AND stay_date >= :checkIn AND stay_date < :checkOut
                           AND booked > 0
                        """)
                .param("rateId", rateId)
                .param("checkIn", checkIn)
                .param("checkOut", checkOut)
                .update();
    }
}
