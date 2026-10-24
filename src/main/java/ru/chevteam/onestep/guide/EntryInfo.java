package ru.chevteam.onestep.guide;

import java.time.LocalDate;

/**
 * Правила въезда для граждан РФ.
 *
 * @param maxDays          сколько дней можно пробыть без визы; {@code null} — ограничение не указано
 * @param internalPassport можно ли въехать по внутреннему паспорту РФ
 * @param checkedOn        дата проверки по источнику — правила меняются, интерфейс показывает её рядом
 */
public record EntryInfo(
        EntryRegime regime,
        Integer maxDays,
        boolean internalPassport,
        String summary,
        String details,
        String sourceUrl,
        LocalDate checkedOn) {
}
