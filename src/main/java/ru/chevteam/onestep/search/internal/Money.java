package ru.chevteam.onestep.search.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Форматирование рублей: «66 600 ₽» (неразрывные пробелы). */
final class Money {

    private static final char NBSP = ' ';

    private Money() {
    }

    static String format(BigDecimal amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator(NBSP);
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        return format.format(amount.setScale(0, RoundingMode.HALF_UP)) + NBSP + "₽";
    }
}
