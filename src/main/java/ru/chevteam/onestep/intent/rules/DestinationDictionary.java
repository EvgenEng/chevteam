package ru.chevteam.onestep.intent.rules;

import ru.chevteam.onestep.intent.Destination;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Словарь направлений: основа слова → направление. Основы ловят все падежи
 * («Турция», «в Турцию», «по Турции»; «Питер», «в Питере»).
 * <p>
 * Города проверяются раньше стран: «Анталья» точнее, чем «Турция».
 */
final class DestinationDictionary {

    private record Entry(Pattern pattern, Destination destination) {
    }

    private static final String TURKEY = "Турция";
    private static final String RUSSIA = "Россия";

    private static final List<Entry> ENTRIES = List.of(
            city("анталь\\w*", TURKEY, "Анталья"),
            city("кемер\\w*", TURKEY, "Кемер"),
            city("белек\\w*", TURKEY, "Белек"),
            city("сиде", TURKEY, "Сиде"),
            city("алань\\w*", TURKEY, "Аланья"),
            city("сочи|адлер\\w*", RUSSIA, "Сочи"),
            city("питер\\w*|петербург\\w*|санкт\\S*|спб", RUSSIA, "Санкт-Петербург"),
            city("москв\\w*", RUSSIA, "Москва"),
            city("казан\\w*", RUSSIA, "Казань"),
            city("калининград\\w*", RUSSIA, "Калининград"),
            country("турц\\w*", TURKEY));

    private DestinationDictionary() {
    }

    /**
     * Находит направление, упомянутое в тексте раньше всех остальных.
     */
    static Optional<Match> find(String normalizedText) {
        Match best = null;
        for (Entry entry : ENTRIES) {
            Matcher m = entry.pattern().matcher(normalizedText);
            if (m.find() && (best == null || m.start() < best.start())) {
                best = new Match(entry.destination(), m.start(), m.end());
            }
        }
        return Optional.ofNullable(best);
    }

    record Match(Destination destination, int start, int end) {
    }

    private static Entry city(String stemRegex, String country, String city) {
        return new Entry(word(stemRegex), Destination.city(country, city));
    }

    private static Entry country(String stemRegex, String country) {
        return new Entry(word(stemRegex), Destination.country(country));
    }

    private static Pattern word(String stemRegex) {
        return Pattern.compile("\\b(?:" + stemRegex + ")\\b", RussianText.FLAGS);
    }
}
