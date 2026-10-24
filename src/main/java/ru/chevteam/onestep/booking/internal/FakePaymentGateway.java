package ru.chevteam.onestep.booking.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Демо-шлюз: никаких реальных денег и реквизитов. Решение зависит только от выбранного тестового способа.
 */
@Component
class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    @Override
    public PaymentResponse charge(PaymentRequest request) {
        log.info("Test payment for {}: {} {} via {}", request.bookingReference(), request.amount(),
                request.currency(), request.method());
        return switch (request.method()) {
            case TEST_CARD_SUCCESS -> PaymentResponse.approved("pay_" + UUID.randomUUID().toString().replace("-", ""));
            case TEST_CARD_DECLINE -> PaymentResponse.declined("Банк отклонил платёж (тестовый отказ)");
        };
    }
}
