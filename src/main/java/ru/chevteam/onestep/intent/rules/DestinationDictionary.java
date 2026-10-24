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
    private static final String THAILAND = "Таиланд";
    private static final String UAE = "ОАЭ";
    private static final String EGYPT = "Египет";
    private static final String VIETNAM = "Вьетнам";
    private static final String GEORGIA = "Грузия";
    private static final String ABKHAZIA = "Абхазия";
    private static final String MALDIVES = "Мальдивы";
    private static final String INDONESIA = "Индонезия";
    private static final String SRI_LANKA = "Шри-Ланка";
    private static final String CHINA = "Китай";
    private static final String JAPAN = "Япония";
    private static final String KOREA = "Южная Корея";
    private static final String CAMBODIA = "Камбоджа";
    private static final String LAOS = "Лаос";
    private static final String SINGAPORE = "Сингапур";
    private static final String MALAYSIA = "Малайзия";
    private static final String PHILIPPINES = "Филиппины";
    private static final String INDIA = "Индия";

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
            city("пхукет\\w*", THAILAND, "Пхукет"),
            city("паттай\\w*", THAILAND, "Паттайя"),
            city("самуи", THAILAND, "Самуи"),
            city("бангкок\\w*", THAILAND, "Бангкок"),
            city("краби", THAILAND, "Краби"),
            city("дуба[йе]\\w*", UAE, "Дубай"),
            city("абу-?\\s?даби", UAE, "Абу-Даби"),
            city("хургад\\w*", EGYPT, "Хургада"),
            city("шарм\\S*", EGYPT, "Шарм-эль-Шейх"),
            city("нячанг\\w*", VIETNAM, "Нячанг"),
            city("фукуок\\w*", VIETNAM, "Фукуок"),
            city("батуми", GEORGIA, "Батуми"),
            city("тбилиси", GEORGIA, "Тбилиси"),
            city("гагр\\w*", ABKHAZIA, "Гагра"),
            city("бали", INDONESIA, "Бали"),
            city("стамбул\\w*", TURKEY, "Стамбул"),
            city("дананг\\w*", VIETNAM, "Дананг"),
            city("ханое?\\w*", VIETNAM, "Ханой"),
            city("хошимин\\w*|сайгон\\w*", VIETNAM, "Хошимин"),
            city("пекин\\w*", CHINA, "Пекин"),
            city("шанха[йе]\\w*", CHINA, "Шанхай"),
            city("сань[яеию]|хайнан\\w*", CHINA, "Санья"),
            city("гуанчжоу", CHINA, "Гуанчжоу"),
            city("харбин\\w*", CHINA, "Харбин"),
            city("токио", JAPAN, "Токио"),
            city("киото", JAPAN, "Киото"),
            city("осак\\w*", JAPAN, "Осака"),
            city("сеул\\w*", KOREA, "Сеул"),
            city("сием\\S*|сиемреап\\w*", CAMBODIA, "Сием-Рип"),
            city("пномпен\\w*", CAMBODIA, "Пномпень"),
            city("луанг\\S*", LAOS, "Луангпрабанг"),
            city("вьентьян\\w*", LAOS, "Вьентьян"),
            city("сингапур\\w*", SINGAPORE, "Сингапур"),
            city("куала\\S*", MALAYSIA, "Куала-Лумпур"),
            city("лангкав\\w*", MALAYSIA, "Лангкави"),
            city("боракай\\w*", PHILIPPINES, "Боракай"),
            city("гоа", INDIA, "Гоа"),
            city("унаватун\\w*", SRI_LANKA, "Унаватуна"),
            city("коломбо", SRI_LANKA, "Коломбо"),
            country("турц\\w*", TURKEY),
            country("та[йи]ланд\\w*", THAILAND),
            country("оаэ|эмират\\w*", UAE),
            country("египт?\\w*", EGYPT),
            country("вьетнам\\w*", VIETNAM),
            country("груз(?:ия|ию|ии|ией)", GEORGIA),
            country("абхаз\\w*", ABKHAZIA),
            country("мальдив\\w*", MALDIVES),
            country("индонези\\w*", INDONESIA),
            country("шри-?\\s?ланк\\w*", SRI_LANKA),
            country("кита(?:й|я|е|ю|ем)", CHINA),
            country("япони\\w*", JAPAN),
            country("(?:южн\\w* )?коре(?:я|ю|и|ей)", KOREA),
            country("камбодж\\w*", CAMBODIA),
            country("лаос\\w*", LAOS),
            country("малайзи\\w*", MALAYSIA),
            country("филиппин\\w*", PHILIPPINES),
            country("инди(?:я|ю|и|ей)", INDIA));

    private DestinationDictionary() {
    }

    /**
     * Находит направление, упомянутое в тексте раньше всех остальных. Если первой названа страна,
     * а дальше — город этой страны («в Таиланд, на Пхукет»), выбирается более точный город.
     */
    static Optional<Match> find(String normalizedText) {
        List<Match> matches = new java.util.ArrayList<>();
        for (Entry entry : ENTRIES) {
            Matcher m = entry.pattern().matcher(normalizedText);
            if (m.find()) {
                matches.add(new Match(entry.destination(), m.start(), m.end(), -1, -1));
            }
        }
        Match best = matches.stream().min(java.util.Comparator.comparingInt(Match::start)).orElse(null);
        if (best != null && best.destination().city() == null) {
            String country = best.destination().country();
            Match countryOnly = best;
            best = matches.stream()
                    .filter(m -> m.destination().city() != null && m.destination().country().equals(country))
                    .min(java.util.Comparator.comparingInt(Match::start))
                    .map(city -> new Match(city.destination(), city.start(), city.end(),
                            countryOnly.start(), countryOnly.end()))
                    .orElse(countryOnly);
        }
        return Optional.ofNullable(best);
    }

    /**
     * @param extraStart второй найденный фрагмент (страна при выбранном городе) или -1
     */
    record Match(Destination destination, int start, int end, int extraStart, int extraEnd) {

        boolean hasExtra() {
            return extraStart >= 0;
        }
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
