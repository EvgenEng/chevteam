package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.MealPlan;

import java.math.BigDecimal;

/**
 * Цена проживания за весь период — то, что пользователь сравнивает со своим бюджетом.
 *
 * @param mealAdded      питание, добавленное к тарифу по запросу пользователя, или {@code null}
 * @param mealAddedTotal сколько стоит это питание за всю поездку (уже входит в {@code total})
 */
public record PriceQuote(BigDecimal total, BigDecimal perNight, int nights, String currency,
                         MealPlan mealAdded, BigDecimal mealAddedTotal) {

    public PriceQuote withMeal(MealPlan plan, BigDecimal addonTotal) {
        return new PriceQuote(total.add(addonTotal), perNight, nights, currency, plan, addonTotal);
    }
}
