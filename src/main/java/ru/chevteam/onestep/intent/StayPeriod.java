package ru.chevteam.onestep.intent;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Период проживания. Пользователь редко называет точные даты — чаще месяц и длительность,
 * поэтому все поля опциональны, а {@link #checkIn()} может быть вычислен (например, «выходные в январе»).
 */
public record StayPeriod(YearMonth month, LocalDate checkIn, Integer nights) {

    public StayPeriod {
        if (nights != null && (nights < 1 || nights > 60)) {
            throw new IllegalArgumentException("nights must be in [1, 60]: " + nights);
        }
        if (checkIn != null && month == null) {
            month = YearMonth.from(checkIn);
        }
    }

    public LocalDate checkOut() {
        return checkIn != null && nights != null ? checkIn.plusDays(nights) : null;
    }
}
