package ru.chevteam.onestep.booking;

/**
 * <pre>
 *            pay ok            cancel
 *   HELD ─────────────▶ CONFIRMED ─────▶ CANCELLED
 *    │  │
 *    │  └─ cancel ─────────────────────▶ CANCELLED
 *    └──── hold TTL прошёл ────────────▶ EXPIRED
 * </pre>
 * Отказ оплаты бронь не меняет: номер остаётся удержанным, можно повторить.
 */
public enum BookingStatus {
    HELD, CONFIRMED, CANCELLED, EXPIRED;

    public boolean isFinal() {
        return this == CANCELLED || this == EXPIRED;
    }
}
