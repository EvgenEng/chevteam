package ru.chevteam.onestep.booking.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ru.chevteam.onestep.booking.BookingStatus;
import ru.chevteam.onestep.booking.BookingView;
import ru.chevteam.onestep.shared.config.CoreConfiguration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Агрегат «бронь». Переходы статусов — только через методы с проверкой инвариантов,
 * сеттеров нет. Параллельные изменения отсекаются оптимистической блокировкой ({@code @Version}).
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String reference;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "rate_id", nullable = false)
    private UUID rateId;

    @Column(name = "hotel_name", nullable = false)
    private String hotelName;

    @Column(nullable = false)
    private String city;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(name = "meal_plan", nullable = false)
    private String mealPlan;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private int adults;

    @Convert(converter = ChildrenAgesConverter.class)
    @Column(name = "children_ages", nullable = false)
    private List<Integer> childrenAges;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "guest_email", nullable = false)
    private String guestEmail;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean refundable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "last_payment_error")
    private String lastPaymentError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Booking() {
        // JPA
    }

    static Booking hold(HoldCommand cmd, PricedRate rate, String reference, Instant now, Duration holdTtl) {
        Booking b = new Booking();
        b.id = UUID.randomUUID();
        b.reference = Objects.requireNonNull(reference);
        b.hotelId = rate.hotelId();
        b.rateId = rate.rateId();
        b.hotelName = rate.hotelName();
        b.city = rate.city();
        b.roomName = rate.roomName();
        b.mealPlan = rate.mealPlan();
        b.checkIn = cmd.checkIn();
        b.checkOut = cmd.checkIn().plusDays(cmd.nights());
        b.adults = cmd.adults();
        b.childrenAges = List.copyOf(cmd.childrenAges());
        b.guestName = cmd.guestName().strip();
        b.guestEmail = cmd.guestEmail().strip().toLowerCase(java.util.Locale.ROOT);
        b.totalPrice = rate.total();
        b.currency = rate.currency();
        b.refundable = rate.refundable();
        b.status = BookingStatus.HELD;
        b.holdExpiresAt = now.plus(holdTtl).truncatedTo(ChronoUnit.SECONDS);
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }

    // ------------------------------------------------------------------ transitions

    /** Проверка перед списанием денег: деньги не должны уйти за бронь, которую уже нельзя подтвердить. */
    void ensurePayable(Instant now) {
        if (status == BookingStatus.CONFIRMED) {
            throw new IllegalBookingStateException("Бронь " + reference + " уже оплачена");
        }
        if (status != BookingStatus.HELD) {
            throw new IllegalBookingStateException("Бронь " + reference + " в статусе " + status + " — оплата невозможна");
        }
        if (!now.isBefore(holdExpiresAt)) {
            throw new IllegalBookingStateException("Время удержания брони " + reference + " истекло — создайте бронь заново");
        }
    }

    void confirm(String paymentId, Instant now) {
        ensurePayable(now);
        this.status = BookingStatus.CONFIRMED;
        this.paymentId = Objects.requireNonNull(paymentId);
        this.lastPaymentError = null;
        this.updatedAt = now;
    }

    void paymentDeclined(String reason, Instant now) {
        ensurePayable(now);
        this.lastPaymentError = reason;
        this.updatedAt = now;
    }

    /** @return была ли бронь оплачена до отмены (нужен возврат) */
    boolean cancel(Instant now) {
        if (status.isFinal()) {
            throw new IllegalBookingStateException("Бронь " + reference + " уже " + (status == BookingStatus.EXPIRED
                    ? "истекла" : "отменена"));
        }
        if (status == BookingStatus.CONFIRMED && !refundable) {
            throw new IllegalBookingStateException("Тариф невозвратный — бесплатная отмена брони " + reference
                    + " недоступна");
        }
        if (status == BookingStatus.CONFIRMED && !now.isBefore(checkIn.atStartOfDay(CoreConfiguration.MOSCOW).toInstant())) {
            throw new IllegalBookingStateException("Отмена после даты заезда невозможна");
        }
        boolean wasConfirmed = status == BookingStatus.CONFIRMED;
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
        return wasConfirmed;
    }

    void expire(Instant now) {
        if (status != BookingStatus.HELD || now.isBefore(holdExpiresAt)) {
            throw new IllegalBookingStateException("Бронь " + reference + " нельзя пометить истёкшей");
        }
        this.status = BookingStatus.EXPIRED;
        this.updatedAt = now;
    }

    /** Номер нужно вернуть в продажу, только если он ещё был за нами закреплён. */
    boolean holdsInventory() {
        return status == BookingStatus.HELD || status == BookingStatus.CONFIRMED;
    }

    // ------------------------------------------------------------------ read

    BookingView toView() {
        return new BookingView(id, reference, status, hotelId, hotelName, city, roomName, mealPlan,
                checkIn, checkOut, nights(), adults, childrenAges, guestName, totalPrice, currency, refundable,
                holdExpiresAt, lastPaymentError);
    }

    int nights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    UUID id() {
        return id;
    }

    String reference() {
        return reference;
    }

    UUID hotelId() {
        return hotelId;
    }

    UUID rateId() {
        return rateId;
    }

    String hotelName() {
        return hotelName;
    }

    String city() {
        return city;
    }

    LocalDate checkIn() {
        return checkIn;
    }

    LocalDate checkOut() {
        return checkOut;
    }

    int guests() {
        return adults + childrenAges.size();
    }

    BigDecimal totalPrice() {
        return totalPrice;
    }

    String currency() {
        return currency;
    }

    BookingStatus status() {
        return status;
    }
}
