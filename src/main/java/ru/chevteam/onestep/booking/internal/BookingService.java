package ru.chevteam.onestep.booking.internal;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chevteam.onestep.booking.BookingCancelled;
import ru.chevteam.onestep.booking.BookingConfirmed;
import ru.chevteam.onestep.booking.BookingExpired;
import ru.chevteam.onestep.booking.BookingView;
import ru.chevteam.onestep.booking.PaymentMethod;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.RateQuote;
import ru.chevteam.onestep.shared.error.BusinessRuleException;
import ru.chevteam.onestep.shared.error.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Сценарии бронирования. Каждый метод — одна транзакция: изменение брони, остатков и запись события
 * в outbox либо происходят вместе, либо не происходят вовсе.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int REFERENCE_ATTEMPTS = 5;

    private final BookingRepository bookings;
    private final InventoryRepository inventory;
    private final HotelCatalog catalog;
    private final PaymentGateway payments;
    private final ApplicationEventPublisher events;
    private final BookingProperties props;
    private final MeterRegistry meters;
    private final Clock clock;

    BookingService(BookingRepository bookings, InventoryRepository inventory, HotelCatalog catalog,
                   PaymentGateway payments, ApplicationEventPublisher events, BookingProperties props,
                   MeterRegistry meters, Clock clock) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.catalog = catalog;
        this.payments = payments;
        this.events = events;
        this.props = props;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional
    public BookingView hold(HoldCommand cmd) {
        Instant now = clock.instant();
        validateStay(cmd);

        RateQuote quote = catalog.quote(cmd.rateId(), cmd.checkIn(), cmd.nights())
                .orElseThrow(() -> new NotFoundException("Тариф " + cmd.rateId() + " не найден"));
        if (cmd.adults() > quote.maxAdults() || cmd.childrenAges().size() > quote.maxChildren()) {
            throw new BusinessRuleException("Номер «" + quote.roomName() + "» рассчитан на " + quote.maxAdults()
                    + " взр." + (quote.maxChildren() > 0 ? " и " + quote.maxChildren() + " дет." : " без детей"));
        }

        inventory.reserve(cmd.rateId(), cmd.checkIn(), cmd.checkIn().plusDays(cmd.nights()));
        Booking booking = Booking.hold(cmd, PricedRate.of(quote), uniqueReference(), now, props.holdTtl());
        bookings.save(booking);

        meters.counter("onestep.booking.transitions", "to", "HELD").increment();
        log.info("Booking {} held: {} × {} nights, {} {}", booking.reference(), quote.hotelName(), cmd.nights(),
                booking.totalPrice(), booking.currency());
        return booking.toView();
    }

    /**
     * Оплата под пессимистической блокировкой строки брони. Отказ банка — штатный исход, а не ошибка:
     * транзакция коммитится, бронь остаётся удержанной, причина отказа сохраняется.
     */
    @Transactional
    public PaymentResult pay(UUID bookingId, PaymentMethod method, String idempotencyKey) {
        Instant now = clock.instant();
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронь " + bookingId + " не найдена"));
        booking.ensurePayable(now);

        PaymentGateway.PaymentResponse response = payments.charge(new PaymentGateway.PaymentRequest(
                idempotencyKey, booking.reference(), booking.totalPrice(), booking.currency(), method));

        if (!response.approved()) {
            booking.paymentDeclined(response.declineReason(), now);
            meters.counter("onestep.booking.payments", "result", "declined").increment();
            return new PaymentResult(booking.toView(), false, response.declineReason());
        }

        booking.confirm(response.paymentId(), now);
        events.publishEvent(new BookingConfirmed(booking.id(), booking.reference(), booking.hotelId(),
                booking.hotelName(), booking.city(), booking.checkIn(), booking.checkOut(), booking.guests(),
                booking.totalPrice(), booking.currency(), now));
        meters.counter("onestep.booking.payments", "result", "approved").increment();
        meters.counter("onestep.booking.transitions", "to", "CONFIRMED").increment();
        log.info("Booking {} confirmed", booking.reference());
        return new PaymentResult(booking.toView(), true, null);
    }

    @Transactional
    public BookingView cancel(UUID bookingId) {
        Instant now = clock.instant();
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронь " + bookingId + " не найдена"));
        boolean wasConfirmed = booking.cancel(now);
        inventory.release(booking.rateId(), booking.checkIn(), booking.checkOut());
        events.publishEvent(new BookingCancelled(booking.id(), booking.reference(), booking.hotelId(),
                booking.checkIn(), booking.checkOut(), wasConfirmed, now));
        meters.counter("onestep.booking.transitions", "to", "CANCELLED").increment();
        log.info("Booking {} cancelled (was confirmed: {})", booking.reference(), wasConfirmed);
        return booking.toView();
    }

    /**
     * Снимает просроченные удержания пачкой и возвращает номера в продажу.
     *
     * @return сколько броней истекло
     */
    @Transactional
    public int expireStaleHolds(int batchSize) {
        Instant now = clock.instant();
        List<Booking> expired = bookings.lockExpiredHolds(now, batchSize);
        for (Booking booking : expired) {
            booking.expire(now);
            inventory.release(booking.rateId(), booking.checkIn(), booking.checkOut());
            events.publishEvent(new BookingExpired(booking.id(), booking.reference(), booking.hotelId(), now));
        }
        if (!expired.isEmpty()) {
            meters.counter("onestep.booking.transitions", "to", "EXPIRED").increment(expired.size());
            log.info("Expired {} stale holds", expired.size());
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public BookingView get(UUID bookingId) {
        return bookings.findById(bookingId)
                .map(Booking::toView)
                .orElseThrow(() -> new NotFoundException("Бронь " + bookingId + " не найдена"));
    }

    @Transactional(readOnly = true)
    public List<BookingView> findByGuestEmail(String email) {
        return bookings.findByGuestEmail(email.strip()).stream().map(Booking::toView).toList();
    }

    private void validateStay(HoldCommand cmd) {
        LocalDate today = LocalDate.now(clock);
        if (cmd.checkIn().isBefore(today)) {
            throw new BusinessRuleException("Дата заезда уже прошла");
        }
        if (cmd.nights() < 1 || cmd.nights() > props.maxNights()) {
            throw new BusinessRuleException("Длительность — от 1 до " + props.maxNights() + " ночей");
        }
    }

    private String uniqueReference() {
        for (int i = 0; i < REFERENCE_ATTEMPTS; i++) {
            String candidate = BookingReferences.next();
            if (!bookings.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate unique booking reference");
    }

    public record PaymentResult(BookingView booking, boolean approved, String declineReason) {
    }
}
