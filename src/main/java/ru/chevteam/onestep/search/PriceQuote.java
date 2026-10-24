package ru.chevteam.onestep.search;

import java.math.BigDecimal;

/**
 * Цена проживания за весь период — то, что пользователь сравнивает со своим бюджетом.
 */
public record PriceQuote(BigDecimal total, BigDecimal perNight, int nights, String currency) {
}
