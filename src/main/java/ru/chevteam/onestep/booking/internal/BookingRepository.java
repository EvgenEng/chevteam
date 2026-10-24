package ru.chevteam.onestep.booking.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Блокировка строки на время оплаты: два параллельных запроса не спишут деньги дважды. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") UUID id);

    /**
     * Просроченные удержания. {@code SKIP LOCKED}: строки, которые прямо сейчас оплачиваются,
     * пропускаются, а несколько экземпляров приложения не мешают друг другу.
     */
    @Query(value = """
            SELECT * FROM booking
             WHERE status = 'HELD' AND hold_expires_at <= :now
             ORDER BY hold_expires_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Booking> lockExpiredHolds(@Param("now") Instant now, @Param("limit") int limit);

    @Query("select b from Booking b where lower(b.guestEmail) = lower(:email) order by b.checkIn desc")
    List<Booking> findByGuestEmail(@Param("email") String email);

    boolean existsByReference(String reference);
}
