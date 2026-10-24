package ru.chevteam.onestep.booking.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);

    @Test
    void holdStartsTimerAndSnapshotsPrice() {
        Booking b = booking(true);

        assertThat(b.status()).isEqualTo(BookingStatus.HELD);
        assertThat(b.toView().holdExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(b.toView().nights()).isEqualTo(9);
        assertThat(b.toView().total()).isEqualByComparingTo("66600");
        assertThat(b.toView().childrenAges()).containsExactly(7);
    }

    @Test
    void paymentConfirmsHeldBooking() {
        Booking b = booking(true);
        b.confirm("pay_1", NOW.plusSeconds(60));
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cannotPayTwice() {
        Booking b = booking(true);
        b.confirm("pay_1", NOW.plusSeconds(60));
        assertThatThrownBy(() -> b.confirm("pay_2", NOW.plusSeconds(61)))
                .isInstanceOf(IllegalBookingStateException.class)
                .hasMessageContaining("уже оплачена");
    }

    @Test
    void cannotPayAfterHoldExpired() {
        Booking b = booking(true);
        assertThatThrownBy(() -> b.confirm("pay_1", NOW.plus(TTL)))
                .isInstanceOf(IllegalBookingStateException.class)
                .hasMessageContaining("истекло");
    }

    @Test
    void declinedPaymentKeepsHold() {
        Booking b = booking(true);
        b.paymentDeclined("Банк отклонил платёж", NOW.plusSeconds(30));
        assertThat(b.status()).isEqualTo(BookingStatus.HELD);
        assertThat(b.toView().lastPaymentError()).isEqualTo("Банк отклонил платёж");
        b.confirm("pay_1", NOW.plusSeconds(60));
        assertThat(b.toView().lastPaymentError()).isNull();
    }

    @Test
    void expireOnlyAfterTtl() {
        Booking b = booking(true);
        assertThatThrownBy(() -> b.expire(NOW.plusSeconds(60))).isInstanceOf(IllegalBookingStateException.class);
        b.expire(NOW.plus(TTL));
        assertThat(b.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(b.holdsInventory()).isFalse();
    }

    @Test
    void cancelReportsWhetherRefundIsNeeded() {
        Booking held = booking(true);
        assertThat(held.cancel(NOW.plusSeconds(10))).isFalse();

        Booking paid = booking(true);
        paid.confirm("pay_1", NOW.plusSeconds(10));
        assertThat(paid.cancel(NOW.plusSeconds(20))).isTrue();
        assertThat(paid.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void nonRefundableConfirmedBookingCannotBeCancelled() {
        Booking b = booking(false);
        b.confirm("pay_1", NOW.plusSeconds(10));
        assertThatThrownBy(() -> b.cancel(NOW.plusSeconds(20)))
                .isInstanceOf(IllegalBookingStateException.class)
                .hasMessageContaining("невозвратный");
    }

    @Test
    void finalStatesAreFinal() {
        Booking b = booking(true);
        b.cancel(NOW.plusSeconds(10));
        assertThatThrownBy(() -> b.cancel(NOW.plusSeconds(20))).isInstanceOf(IllegalBookingStateException.class);
        assertThatThrownBy(() -> b.confirm("pay_1", NOW.plusSeconds(20))).isInstanceOf(IllegalBookingStateException.class);
    }

    @Test
    void referencesAreReadable() {
        for (int i = 0; i < 1000; i++) {
            assertThat(BookingReferences.next()).matches("OS-[A-HJ-NP-Z2-9]{6}");
        }
    }

    @Test
    void childrenAgesRoundTrip() {
        ChildrenAgesConverter converter = new ChildrenAgesConverter();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(List.of(7, 10))))
                .containsExactly(7, 10);
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    private static Booking booking(boolean refundable) {
        HoldCommand cmd = new HoldCommand(UUID.randomUUID(), LocalDate.of(2027, 5, 10), 9, 2, List.of(7),
                "  Евгений  ", "Guest@Example.com");
        PricedRate rate = new PricedRate(cmd.rateId(), UUID.randomUUID(), "Aqua Riviera", "Белек", "Стандарт",
                "ALL_INCLUSIVE", refundable, new BigDecimal("66600"), "RUB");
        return Booking.hold(cmd, rate, BookingReferences.next(), NOW, TTL);
    }
}
