package ru.chevteam.onestep.booking.internal;

import ru.chevteam.onestep.booking.PaymentMethod;

import java.math.BigDecimal;

/**
 * Порт платёжного провайдера. В демо — {@link FakePaymentGateway}; боевой адаптер
 * (ЮKassa, CloudPayments и т.п.) реализует тот же контракт.
 */
interface PaymentGateway {

    PaymentResponse charge(PaymentRequest request);

    record PaymentRequest(String idempotencyKey, String bookingReference, BigDecimal amount, String currency,
                          PaymentMethod method) {
    }

    record PaymentResponse(boolean approved, String paymentId, String declineReason) {

        static PaymentResponse approved(String paymentId) {
            return new PaymentResponse(true, paymentId, null);
        }

        static PaymentResponse declined(String reason) {
            return new PaymentResponse(false, null, reason);
        }
    }
}
