package ru.chevteam.onestep.booking.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param holdTtl сколько номер держится за гостем до оплаты
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration holdTtl, int maxNights) {

    public BookingProperties {
        holdTtl = holdTtl == null ? Duration.ofMinutes(15) : holdTtl;
        maxNights = maxNights <= 0 ? 30 : maxNights;
    }
}
