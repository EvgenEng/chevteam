package ru.chevteam.onestep.intent;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Бюджет в рублях. Пользователи думают о стоимости всей поездки («до 80 тысяч»),
 * а не о цене за ночь — поэтому скоуп явный.
 */
public record Budget(BigDecimal amount, Scope scope) {

    public enum Scope { TOTAL, PER_NIGHT }

    public Budget {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(scope, "scope");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("budget must be positive: " + amount);
        }
    }

    public static Budget total(long rubles) {
        return new Budget(BigDecimal.valueOf(rubles), Scope.TOTAL);
    }

    public static Budget perNight(long rubles) {
        return new Budget(BigDecimal.valueOf(rubles), Scope.PER_NIGHT);
    }

    /** Бюджет на всё проживание. */
    public BigDecimal totalFor(int nights) {
        return scope == Scope.TOTAL ? amount : amount.multiply(BigDecimal.valueOf(nights));
    }
}
