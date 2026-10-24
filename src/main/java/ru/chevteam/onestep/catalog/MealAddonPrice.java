package ru.chevteam.onestep.catalog;

import java.math.BigDecimal;

/**
 * Питание, которое можно добавить к тарифу отеля, — цена за человека в сутки.
 *
 * @param mealPlan       BREAKFAST, HALF_BOARD, FULL_BOARD или ALL_INCLUSIVE
 * @param childFreeUnder дети младше этого возраста едят бесплатно
 */
public record MealAddonPrice(String mealPlan, BigDecimal adultPerNight, BigDecimal childPerNight, int childFreeUnder) {
}
