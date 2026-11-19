package ru.chevteam.onestep.trip.internal;

import ru.chevteam.onestep.trip.TransportMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Что в тексте поездки относится к дороге, а не к отелю: откуда едем, на чём, резерв на личные расходы.
 * Эти слова вырезаются перед поиском отеля — иначе «трансфер» стал бы требованием к отелю,
 * а «из Москвы» — направлением.
 */
final class TripText {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern ORIGIN = Pattern.compile("(?<![а-яё])из\\s+([а-яё-]+)(\\s+[а-яё-]+)?", FLAGS);
    private static final Pattern HOTEL_ONLY = Pattern.compile(
            "только (?:отель|жиль[её]|проживание)|без (?:дороги|билетов|трансфер\\w*)|доберемся сами|доберёмся сами|сами доед\\w*", FLAGS);
    private static final Pattern PLANE = Pattern.compile("самол[её]т\\w*|перел[её]т\\w*|(?<![а-яё])лет(?:им|еть)\\b|авиа\\w*", FLAGS);
    private static final Pattern TRAIN = Pattern.compile("поезд\\w*|(?<![а-яё])ж/?д(?![а-яё])|на ласточке", FLAGS);
    private static final Pattern CAR = Pattern.compile("на (?:своей |нашей )?(?:машин\\w*|авто(?!бус)\\w*)|на сво[её]м авто\\w*", FLAGS);

    private static final String MONEY = "(\\d[\\d\\s ]*(?:[.,]\\d+)?)\\s*(тыс\\w*|т\\.?\\s?р\\.?|к(?![а-яё])|млн\\w*)?\\s*(?:руб\\w*|р\\.?|₽)?";
    private static final Pattern RESERVE = Pattern.compile(
            "(?:резерв\\w*|(?:оставить|оставим|отложить|отложим)|на (?:личные|питание|еду|расходы|сувенир\\w*|экскурси\\w*))"
                    + "[^\\d.;]{0,60}?" + MONEY, FLAGS);
    private static final Pattern NO_RESERVE = Pattern.compile("без (?:отдельного )?резерв\\w*|резерв\\w* (?:не нужен|0\\b)|^\\s*(?:нет|не нужно|0)\\s*$", FLAGS);
    private static final Pattern ANY_MONEY = Pattern.compile(MONEY, FLAGS);

    /** Слова о дороге, которые не должны влиять на подбор отеля. */
    private static final Pattern TRAVEL_WORDS = Pattern.compile(
            "(?:на |включая |плюс |\\+ ?)?(?:дорог[уиа]|билет\\w*|проезд\\w*)(?: туда[- ]и[- ]обратно)?"
                    + "|оба трансфера|трансфер\\w*|туда[- ]и[- ]обратно"
                    + "|самол[её]т\\w*|перел[её]т\\w*|поезд\\w*|на (?:своей |нашей )?машин\\w*|на сво[её]м авто\\w*", FLAGS);

    /**
     * @param origin      город отправления, если он назван в тексте
     * @param unknownOrigin «из Урюпинска» — назван, но не найден в справочнике
     * @param reserve     резерв, если назван
     * @param searchText  текст для поиска отеля — без слов о дороге
     */
    record Parsed(Places.City origin, String unknownOrigin, boolean hotelOnly, TransportMode transport,
                  BigDecimal reserve, String searchText) {
    }

    private TripText() {
    }

    static Parsed read(String text) {
        String t = text == null ? "" : text;
        Places.City origin = null;
        String unknown = null;
        StringBuilder search = new StringBuilder();
        Matcher m = ORIGIN.matcher(t);
        int last = 0;
        while (m.find()) {
            String two = m.group(2) == null ? null : m.group(1) + m.group(2);
            Optional<Places.City> found = two == null ? Optional.empty() : Places.origin(two);
            int end = m.end();
            if (found.isEmpty()) {
                found = Places.origin(m.group(1));
                end = m.end(1);
            }
            if (found.isPresent()) {
                if (origin == null) {
                    origin = found.get();
                }
                search.append(t, last, m.start());
                last = end;
            } else if (unknown == null && Character.isUpperCase(m.group(1).charAt(0))) {
                unknown = m.group(1);
            }
        }
        search.append(t.substring(last));

        BigDecimal reserve = null;
        String s = search.toString();
        Matcher r = RESERVE.matcher(s);
        if (NO_RESERVE.matcher(s).find()) {
            reserve = BigDecimal.ZERO;
            s = NO_RESERVE.matcher(s).replaceAll(" ");
        } else if (r.find()) {
            reserve = money(r.group(1), r.group(2)).orElse(null);
            s = s.substring(0, r.start()) + " " + s.substring(r.end());
        }

        TransportMode transport = CAR.matcher(t).find() ? TransportMode.CAR
                : TRAIN.matcher(t).find() ? TransportMode.TRAIN
                : PLANE.matcher(t).find() ? TransportMode.PLANE : null;
        boolean hotelOnly = HOTEL_ONLY.matcher(t).find();
        s = HOTEL_ONLY.matcher(s).replaceAll(" ");
        s = TRAVEL_WORDS.matcher(s).replaceAll(" ");
        s = s.replaceAll("(?:\\s*[,;]\\s*)+(?=[,;.]|$)", "")
                .replaceAll("\\s+(?:и|,)\\s*(?=[.,;]|$)", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
        return new Parsed(origin, unknown, hotelOnly, transport, reserve, s);
    }

    /** Ответ на вопрос о резерве: «без отдельного резерва», «оставить 10 000 ₽», «20 тысяч». */
    static Optional<BigDecimal> reserveAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        if (NO_RESERVE.matcher(answer).find()) {
            return Optional.of(BigDecimal.ZERO);
        }
        Matcher m = ANY_MONEY.matcher(answer);
        return m.find() ? money(m.group(1), m.group(2)) : Optional.empty();
    }

    static Optional<BigDecimal> money(String digits, String unit) {
        String cleaned = digits.replaceAll("[\\s ]", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String u = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
        if (u.startsWith("млн")) {
            value = value.multiply(BigDecimal.valueOf(1_000_000));
        } else if (!u.isEmpty()) {
            value = value.multiply(BigDecimal.valueOf(1_000));
        } else if (value.compareTo(BigDecimal.valueOf(500)) < 0 && value.signum() > 0) {
            value = value.multiply(BigDecimal.valueOf(1_000)); // «резерв 20» — тысячи
        }
        return Optional.of(value.setScale(0, RoundingMode.HALF_UP));
    }
}
