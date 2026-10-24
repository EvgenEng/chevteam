package ru.chevteam.onestep.booking.internal;

import ru.chevteam.onestep.catalog.RateQuote;

import java.math.BigDecimal;
import java.util.UUID;

/** Снимок тарифа и цены на момент бронирования — дальнейшие изменения каталога бронь не затрагивают. */
record PricedRate(UUID rateId, UUID hotelId, String hotelName, String city, String roomName, String mealPlan,
                  boolean refundable, BigDecimal total, String currency) {

    PricedRate withMeal(String plan, BigDecimal extra) {
        return new PricedRate(rateId, hotelId, hotelName, city, roomName, plan, refundable, total.add(extra), currency);
    }

    static PricedRate of(RateQuote q) {
        return new PricedRate(q.rateId(), q.hotelId(), q.hotelName(), q.city(), q.roomName(), q.mealPlan(),
                q.refundable(), q.total(), q.currency());
    }
}
