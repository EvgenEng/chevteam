package ru.chevteam.onestep.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Пересчёт конкретной поездки.
 *
 * @param origin    город отправления; {@code null} — только отель, без дороги
 * @param transport выбранный вид транспорта или {@code null} — система выберет сама
 * @param budget    бюджет на всю поездку или {@code null}
 * @param reserve   сколько из бюджета оставить на питание и личные расходы
 */
public record TripQuote(UUID hotelId, String origin, TransportMode transport, LocalDate checkIn, int nights,
                        int adults, List<Integer> childrenAges, BigDecimal budget, BigDecimal reserve) {

    public TripQuote {
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        reserve = reserve == null ? BigDecimal.ZERO : reserve;
    }
}
