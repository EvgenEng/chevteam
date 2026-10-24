package ru.chevteam.onestep.intent.rules;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Утилиты для разбора русского текста: нормализация и числительные.
 */
final class RussianText {

    /** Флаги, без которых {@code \b} и {@code \w} не работают с кириллицей. */
    static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    /** Числительные, которые реально встречаются в запросах про поездки. */
    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("один", 1), Map.entry("одна", 1), Map.entry("одну", 1), Map.entry("одни", 1),
            Map.entry("два", 2), Map.entry("две", 2), Map.entry("двое", 2), Map.entry("двоих", 2),
            Map.entry("три", 3), Map.entry("трое", 3), Map.entry("троих", 3),
            Map.entry("четыре", 4), Map.entry("четверо", 4),
            Map.entry("пять", 5), Map.entry("пятеро", 5),
            Map.entry("шесть", 6), Map.entry("семь", 7), Map.entry("восемь", 8),
            Map.entry("девять", 9), Map.entry("десять", 10), Map.entry("одиннадцать", 11),
            Map.entry("двенадцать", 12), Map.entry("четырнадцать", 14),
            Map.entry("пятнадцать", 15), Map.entry("двадцать", 20), Map.entry("тридцать", 30));

    /** Альтернатива для regex: «\d+» или любое числительное из словаря. */
    static final String NUMBER = "(\\d{1,3}|" + String.join("|", NUMBER_WORDS.keySet()) + ")";

    private RussianText() {
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace(' ', ' ')
                .replaceAll("[«»\"“”]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    static OptionalInt parseNumber(String token) {
        if (token == null) {
            return OptionalInt.empty();
        }
        String t = token.strip();
        if (t.chars().allMatch(Character::isDigit) && !t.isEmpty()) {
            return OptionalInt.of(Integer.parseInt(t));
        }
        Integer value = NUMBER_WORDS.get(t);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
