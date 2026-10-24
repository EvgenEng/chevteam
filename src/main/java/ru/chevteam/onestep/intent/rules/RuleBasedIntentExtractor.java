package ru.chevteam.onestep.intent.rules;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.intent.TransportPreference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.chevteam.onestep.intent.rules.RussianText.FLAGS;
import static ru.chevteam.onestep.intent.rules.RussianText.NUMBER;

/**
 * Детерминированный разбор запроса на русском языке.
 * <p>
 * Работает всегда: без сети, за микросекунды, предсказуемо. Используется как
 * fallback при недоступности LLM и как источник «точных» фактов для валидации ответа LLM.
 * <p>
 * Найденные фрагменты «вычёркиваются» из рабочего текста, чтобы одно и то же число
 * не было распознано дважды (например, «до 15 минут» не станет бюджетом).
 */
public final class RuleBasedIntentExtractor implements IntentExtractor {

    static final int DEFAULT_NIGHTS = 7;
    static final int DEFAULT_WEEKEND_NIGHTS = 2;
    static final int DEFAULT_ADULTS = 2;
    static final int UNKNOWN_CHILD_AGE = 8;
    static final int DEFAULT_WALK_MINUTES = 15;

    private static final String MONTH_WORD = "(январ[ьяе]|феврал[ьяе]|март[ае]?|апрел[ьяе]|ма[йяе]|июн[ьяе]|"
            + "июл[ьяе]|август[ае]?|сентябр[ьяе]|октябр[ьяе]|ноябр[ьяе]|декабр[ьяе])";

    private static final Pattern ORIGIN = p("\\bиз\\s+(москв\\w*|питер\\w*|петербург\\w*|санкт\\S*|спб|казан\\w*"
            + "|екатеринбург\\w*|новосибирск\\w*|нижн\\w* новгород\\w*|самар\\w*)");
    private static final Pattern DIRECT_FLIGHT = p("\\bпрям(?:ой|ым|ые|ого|ую|ых)\\b|\\bбез пересад\\w*");

    private static final Pattern DATE_RANGE = p("\\b(?:с\\s*)?(\\d{1,2})\\s*(?:по|-|–|до)\\s*(\\d{1,2})\\s+" + MONTH_WORD + "\\b");
    private static final Pattern MONTH = p("\\b" + MONTH_WORD + "\\b");
    private static final Pattern WEEKEND = p("\\bвыходн\\w*");

    private static final Pattern NIGHTS = p("\\b" + NUMBER + "\\s*ноч\\w*");
    private static final Pattern DAYS_AND_NIGHTS = p("\\b" + NUMBER + "\\s*сут\\w*");
    private static final Pattern DAYS = p("\\b" + NUMBER + "\\s*(?:дн\\w*|день)\\b");
    private static final Pattern WEEKS = p("\\b(?:" + NUMBER + "\\s*)?недел\\w*");

    private static final Pattern PROXIMITY_TO = p("\\bдо\\s+((?:\\p{L}[\\p{L}-]*\\s+){0,2}\\p{L}[\\p{L}-]{2,})\\s+пешком(?:\\s+минут\\w*)?(?:\\s+за)?(?:\\s+(\\d{1,3}))?(?:\\s*мин\\w*)?");
    private static final Pattern PROXIMITY_MINUTES_FIRST = p("\\b(\\d{1,3})\\s*мин\\w*\\s+(?:пешком|ходьбы)\\s+(?:до|от)\\s+(\\p{L}[\\p{L}-]{2,})");
    private static final Pattern PROXIMITY_NEAR = p("\\b(?:рядом с|возле|около|недалеко от)\\s+(\\p{L}[\\p{L}-]{2,})");
    private static final Pattern SEA_OR_BEACH = p("^(?:мор|пляж|берег)\\w*");
    private static final Pattern CENTER_WORD = p("^центр\\w*");

    private static final Pattern BUDGET = p("\\b(?:до|не дороже|не более|максимум|в пределах|бюджет(?:ом)?(?:\\s+до)?|за)\\s*"
            + "(\\d[\\d ]*(?:[.,]\\d+)?)\\s*(тысяч\\w*|тыс\\.?|т\\.?р\\.?|к|k|млн)?(?![\\p{L}\\d])");
    private static final Pattern BUDGET_SHORT = p("\\b(\\d{2,4})\\s*(к|k|тыс\\.?|тысяч\\w*)(?![\\p{L}\\d])");
    private static final Pattern NOT_MONEY_UNIT = p("^\\s*(?:мин|км|м\\b|метр|звезд|зв\\b|лет|год|ноч|дн|день|сут|час|недел|" + MONTH_WORD + ")");
    private static final Pattern PER_NIGHT = p("^\\s*(?:₽|руб\\w*|р\\.?)?\\s*(?:за|в|/)\\s*(?:ночь|сутки)\\b");

    private static final Pattern STARS_DIGIT = p("\\b([1-5])\\s*(?:\\*|★|⭐|звезд\\w*|зв\\.?)");
    private static final Pattern STARS_WORD = p("\\b(пяти|четыр[её]х|тр[её]х)\\s*-?\\s*звезд\\w*");

    private static final Pattern ADULTS = p("\\b" + NUMBER + "\\s*взросл\\w*");
    private static final Pattern SOLO = p("\\b(?:один|одна)\\s+(?:еду|поеду|лечу|путешеству\\w*)\\b|\\b(?:еду|поеду|лечу)\\s+(?:один|одна)\\b"
            + "|\\bсоло\\b|\\bв одиночку\\b|\\bодному\\b");
    private static final Pattern PARTY_SIZE = p("\\bнас\\s+" + NUMBER + "\\b|\\b" + NUMBER + "\\s+человек\\w*");
    private static final Pattern COUPLE = p("\\bвдвоем\\b|\\bна двоих\\b|\\bс (?:женой|мужем|девушкой|парнем|подругой|другом)\\b");
    private static final String CHILD_WORD = "(?:ребен(?:ок|ка|ку|ком)|дет(?:и|ей|ям|ьми)|сын\\w*|доч(?:ь|ка|кой|ерью|ки|ерьми)|малыш\\w*)";
    private static final Pattern CHILDREN_WITH_AGES = p("\\b(?:" + NUMBER + "\\s+)?" + CHILD_WORD
            + "\\s*,?\\s*(?:(?:возраст\\w*|по)\\s*)?((?:\\d{1,2}\\s*(?:,|и)\\s*)*\\d{1,2})\\s*(?:лет|год\\w*|мес\\w*)\\b");
    private static final Pattern CHILDREN_NO_AGE = p("\\b(?:" + NUMBER + "\\s+)?" + CHILD_WORD + "\\b");
    private static final Pattern AGE_LIST_SPLIT = p("\\s*(?:,|и)\\s*");

    private static final Map<MealPlan, Pattern> MEAL_PATTERNS = linkedMap(
            MealPlan.ALL_INCLUSIVE, p("вс[её] включено|all\\s*inclusive|ультра вс\\w*|\\bu?ai\\b|олл инклюзив"),
            MealPlan.HALF_BOARD, p("полупансион|\\bhb\\b|завтрак\\w*\\s*(?:и|\\+)\\s*ужин"),
            MealPlan.FULL_BOARD, p("полн\\w* пансион|\\bfb\\b|тр[её]хразов\\w*"),
            MealPlan.BREAKFAST, p("завтрак|\\bbb\\b"),
            MealPlan.ROOM_ONLY, p("без питания"));

    private static final Map<Amenity, Pattern> AMENITY_PATTERNS = linkedMap(
            Amenity.AQUAPARK, p("аквапарк\\w*|водн\\w* горк\\w*|\\bгорк(?:и|ами|ой)\\b"),
            Amenity.POOL, p("бассейн\\w*"),
            Amenity.KIDS_CLUB, p("детск\\w* клуб\\w*|аниматор\\w*|анимаци\\w*"),
            Amenity.SPA, p("\\bспа\\b|\\bspa\\b|саун\\w*|хаммам\\w*"),
            Amenity.PARKING, p("парковк\\w*|паркинг\\w*"),
            Amenity.GYM, p("фитнес\\w*|спортзал\\w*|тренаж\\w*"));

    private static final Map<SoftPreference, Pattern> SOFT_PATTERNS = linkedMap(
            SoftPreference.QUIET, p("\\bтих\\w*|\\bспокойн\\w*|без шума|не шумн\\w*"),
            SoftPreference.CLEAN, p("\\bчист\\w*"),
            SoftPreference.GOOD_WIFI, p("wi-?fi|вай-?фай|интернет\\w*"));

    private static final Map<LocationFeature, Pattern> LOCATION_PATTERNS = linkedMap(
            LocationFeature.FIRST_LINE, p("перв\\w* линии?|\\b1(?:-?й)? линии?|на берегу|у (?:самого )?моря|на пляже"),
            LocationFeature.CITY_CENTER, p("\\bв центре\\b|\\bцентр(?:е|а)?\\b"));

    private final Clock clock;

    public RuleBasedIntentExtractor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExtractionResult extract(String query) {
        RuleAnalysis analysis = analyze(query);
        return new ExtractionResult(analysis.intent(), IntentSource.RULES,
                analysis.assumptions().stream().map(Note::text).toList(),
                analysis.clarifications().stream().map(Note::text).toList());
    }

    /**
     * Разбор с метаданными о происхождении каждого поля — нужен для слияния с ответом LLM.
     */
    public RuleAnalysis analyze(String query) {
        Objects.requireNonNull(query, "query");
        LocalDate today = LocalDate.now(clock);
        WorkText text = new WorkText(RussianText.normalize(query));
        Ctx ctx = new Ctx();

        TransportPreference transport = ctx.track(IntentField.TRANSPORT, parseTransport(text));
        Destination destination = ctx.track(IntentField.DESTINATION, DestinationDictionary.find(text.value())
                .map(match -> {
                    text.consume(match.start(), match.end());
                    if (match.hasExtra()) {
                        text.consume(match.extraStart(), match.extraEnd());
                    }
                    return match.destination();
                })
                .orElse(null));

        Set<LocationFeature> locationFeatures = EnumSet.noneOf(LocationFeature.class);
        List<ProximityConstraint> proximity = parseProximity(text, locationFeatures);
        Budget budget = ctx.track(IntentField.BUDGET, parseBudget(text, ctx));
        Integer minStars = ctx.track(IntentField.STARS, parseStars(text));
        StayPeriod stay = parseStay(text, today, ctx);
        Guests guests = parseGuests(text, ctx);

        MealPlan mealPlan = ctx.track(IntentField.MEAL_PLAN, firstMatch(text, MEAL_PATTERNS).orElse(null));
        Set<Amenity> amenities = allMatches(text, AMENITY_PATTERNS, Amenity.class);
        Set<SoftPreference> soft = allMatches(text, SOFT_PATTERNS, SoftPreference.class);
        locationFeatures.addAll(allMatches(text, LOCATION_PATTERNS, LocationFeature.class));

        if (destination == null) {
            ctx.clarify(IntentField.DESTINATION,
                    "Куда хотите поехать? Можно описать атмосферу — например, «тёплое море и горы».");
        }
        if (stay.month() == null) {
            ctx.clarify(IntentField.MONTH, "Когда планируете поездку? От сезона сильно зависит цена.");
        }

        SearchIntent intent = new SearchIntent(destination, stay, guests, budget, minStars, mealPlan,
                amenities, soft, locationFeatures, proximity, transport);
        return new RuleAnalysis(intent, ctx.explicit, ctx.assumptions, ctx.clarifications);
    }

    // ---------------------------------------------------------------- transport

    private static TransportPreference parseTransport(WorkText text) {
        String origin = null;
        Matcher m = ORIGIN.matcher(text.value());
        if (m.find()) {
            origin = DestinationDictionary.find(m.group(1))
                    .map(match -> match.destination().displayName())
                    .orElse(null);
            text.consume(m.start(), m.end());
        }
        boolean direct = DIRECT_FLIGHT.matcher(text.value()).find();
        return origin == null && !direct ? null : new TransportPreference(origin, direct);
    }

    // ---------------------------------------------------------------- proximity

    private static List<ProximityConstraint> parseProximity(WorkText text, Set<LocationFeature> features) {
        List<ProximityConstraint> result = new ArrayList<>();
        Matcher m = PROXIMITY_TO.matcher(text.value());
        while (m.find()) {
            int minutes = m.group(2) != null ? Integer.parseInt(m.group(2)) : DEFAULT_WALK_MINUTES;
            addProximity(m.group(1), minutes, result, features);
            text.consume(m.start(), m.end());
        }
        m = PROXIMITY_MINUTES_FIRST.matcher(text.value());
        while (m.find()) {
            addProximity(m.group(2), Integer.parseInt(m.group(1)), result, features);
            text.consume(m.start(), m.end());
        }
        m = PROXIMITY_NEAR.matcher(text.value());
        while (m.find()) {
            addProximity(m.group(1), 10, result, features);
            text.consume(m.start(), m.end());
        }
        return result;
    }

    private static void addProximity(String word, int minutes, List<ProximityConstraint> sink,
                                     Set<LocationFeature> features) {
        if (SEA_OR_BEACH.matcher(word).find()) {
            features.add(LocationFeature.FIRST_LINE);
        } else if (CENTER_WORD.matcher(word).find()) {
            features.add(LocationFeature.CITY_CENTER);
        } else if (minutes >= 1 && minutes <= 120) {
            sink.add(new ProximityConstraint(word, minutes));
        }
    }

    // ---------------------------------------------------------------- budget

    private static Budget parseBudget(WorkText text, Ctx ctx) {
        for (Pattern pattern : List.of(BUDGET, BUDGET_SHORT)) {
            Matcher m = pattern.matcher(text.value());
            while (m.find()) {
                String tail = text.value().substring(m.end());
                if (NOT_MONEY_UNIT.matcher(tail).find()) {
                    continue;
                }
                Optional<BigDecimal> amount = parseMoney(m.group(1), m.group(2), ctx);
                if (amount.isEmpty()) {
                    continue;
                }
                Budget.Scope scope = PER_NIGHT.matcher(tail).find() ? Budget.Scope.PER_NIGHT : Budget.Scope.TOTAL;
                text.consume(m.start(), m.end());
                return new Budget(amount.get(), scope);
            }
        }
        return null;
    }

    private static Optional<BigDecimal> parseMoney(String digits, String unit, Ctx ctx) {
        String cleaned = digits.replace(" ", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(cleaned);
        if (unit != null) {
            BigDecimal multiplier = unit.startsWith("млн") ? BigDecimal.valueOf(1_000_000) : BigDecimal.valueOf(1_000);
            value = value.multiply(multiplier);
        } else if (value.compareTo(BigDecimal.valueOf(500)) < 0) {
            // «до 80» в контексте поездки почти всегда означает тысячи
            value = value.multiply(BigDecimal.valueOf(1_000));
            ctx.assume(IntentField.BUDGET,
                    "Бюджет «" + digits.strip() + "» поняли как " + value.toPlainString() + " ₽");
        }
        value = value.setScale(0, RoundingMode.HALF_UP);
        return value.signum() > 0 ? Optional.of(value) : Optional.empty();
    }

    // ---------------------------------------------------------------- stars

    private static Integer parseStars(WorkText text) {
        Matcher m = STARS_DIGIT.matcher(text.value());
        if (m.find()) {
            text.consume(m.start(), m.end());
            return Integer.parseInt(m.group(1));
        }
        m = STARS_WORD.matcher(text.value());
        if (m.find()) {
            text.consume(m.start(), m.end());
            String w = m.group(1);
            return w.startsWith("пят") ? 5 : w.startsWith("четыр") ? 4 : 3;
        }
        return null;
    }

    // ---------------------------------------------------------------- stay

    private static StayPeriod parseStay(WorkText text, LocalDate today, Ctx ctx) {
        Matcher range = DATE_RANGE.matcher(text.value());
        if (range.find()) {
            int month = monthNumber(range.group(3));
            YearMonth ym = nearestFuture(month, today);
            int fromDay = Integer.parseInt(range.group(1));
            int toDay = Integer.parseInt(range.group(2));
            if (fromDay >= 1 && toDay > fromDay && toDay <= ym.lengthOfMonth()) {
                text.consume(range.start(), range.end());
                ctx.explicit.addAll(List.of(IntentField.MONTH, IntentField.CHECK_IN, IntentField.NIGHTS));
                return new StayPeriod(ym, ym.atDay(fromDay), toDay - fromDay);
            }
        }

        YearMonth month = null;
        Matcher m = MONTH.matcher(text.value());
        if (m.find()) {
            month = nearestFuture(monthNumber(m.group(1)), today);
            text.consume(m.start(), m.end());
            ctx.explicit.add(IntentField.MONTH);
        }

        boolean weekend = WEEKEND.matcher(text.value()).find();
        Integer nights = parseNights(text, ctx);
        if (nights != null) {
            ctx.explicit.add(IntentField.NIGHTS);
        } else {
            nights = weekend ? DEFAULT_WEEKEND_NIGHTS : DEFAULT_NIGHTS;
            ctx.assume(IntentField.NIGHTS, weekend
                    ? "Выходные — считаем 2 ночи (пятница–воскресенье)"
                    : "Длительность не указана — считаем " + DEFAULT_NIGHTS + " ночей");
        }

        LocalDate checkIn = null;
        if (weekend && month != null) {
            LocalDate firstDay = month.atDay(1).isBefore(today) ? today : month.atDay(1);
            checkIn = firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            if (!YearMonth.from(checkIn).equals(month)) {
                checkIn = null;
            }
        }
        if (checkIn != null) {
            ctx.explicit.add(IntentField.CHECK_IN);
        }
        if (month == null) {
            ctx.assume(IntentField.MONTH, "Даты не указаны — цены ориентировочные на ближайший месяц");
        }
        return new StayPeriod(month, checkIn, nights);
    }

    private static Integer parseNights(WorkText text, Ctx ctx) {
        Matcher m = NIGHTS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                return clampNights(n.getAsInt());
            }
        }
        m = DAYS_AND_NIGHTS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                return clampNights(n.getAsInt());
            }
        }
        m = DAYS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                // «10 дней» в туризме — это 9 ночей
                int nights = clampNights(n.getAsInt() - 1);
                ctx.assume(IntentField.NIGHTS, n.getAsInt() + " дн. — это " + nights + " ноч. проживания");
                return nights;
            }
        }
        m = WEEKS.matcher(text.value());
        if (m.find()) {
            int weeks = RussianText.parseNumber(m.group(1)).orElse(1);
            text.consume(m.start(), m.end());
            return clampNights(weeks * 7);
        }
        return null;
    }

    private static int clampNights(int nights) {
        return Math.max(1, Math.min(60, nights));
    }

    private static int monthNumber(String word) {
        String w = word.toLowerCase(java.util.Locale.ROOT);
        if (w.startsWith("янв")) return 1;
        if (w.startsWith("фев")) return 2;
        if (w.startsWith("мар")) return 3;
        if (w.startsWith("апр")) return 4;
        if (w.startsWith("ма")) return 5;
        if (w.startsWith("июн")) return 6;
        if (w.startsWith("июл")) return 7;
        if (w.startsWith("авг")) return 8;
        if (w.startsWith("сен")) return 9;
        if (w.startsWith("окт")) return 10;
        if (w.startsWith("ноя")) return 11;
        if (w.startsWith("дек")) return 12;
        throw new IllegalArgumentException("Unknown month: " + word);
    }

    private static YearMonth nearestFuture(int month, LocalDate today) {
        YearMonth candidate = YearMonth.of(today.getYear(), month);
        return candidate.isBefore(YearMonth.from(today)) ? candidate.plusYears(1) : candidate;
    }

    // ---------------------------------------------------------------- guests

    private static Guests parseGuests(WorkText text, Ctx ctx) {
        Integer adults = null;
        Matcher m = ADULTS.matcher(text.value());
        if (m.find()) {
            adults = RussianText.parseNumber(m.group(1)).stream().boxed().findFirst().orElse(null);
            text.consume(m.start(), m.end());
        } else if (SOLO.matcher(text.value()).find()) {
            adults = 1;
        } else if (COUPLE.matcher(text.value()).find()) {
            adults = 2;
        }
        Integer partySize = null;
        Matcher party = PARTY_SIZE.matcher(text.value());
        if (party.find()) {
            String token = party.group(1) != null ? party.group(1) : party.group(2);
            partySize = RussianText.parseNumber(token).stream().boxed().findFirst().orElse(null);
            text.consume(party.start(), party.end());
        }

        List<Integer> ages = new ArrayList<>();
        int unknownAges = 0;
        m = CHILDREN_WITH_AGES.matcher(text.value());
        while (m.find()) {
            List<Integer> found = new ArrayList<>();
            boolean months = m.group().contains("мес");
            for (String part : AGE_LIST_SPLIT.split(m.group(2).strip())) {
                int age = months ? 0 : Integer.parseInt(part.strip());
                if (age <= 17) {
                    found.add(age);
                }
            }
            int declared = RussianText.parseNumber(m.group(1)).orElse(found.size());
            ages.addAll(found);
            unknownAges += Math.max(0, declared - found.size());
            text.consume(m.start(), m.end());
        }
        m = CHILDREN_NO_AGE.matcher(text.value());
        while (m.find()) {
            unknownAges += RussianText.parseNumber(m.group(1)).orElse(1);
            text.consume(m.start(), m.end());
        }
        if (unknownAges > 0) {
            for (int i = 0; i < unknownAges; i++) {
                ages.add(UNKNOWN_CHILD_AGE);
            }
            ctx.clarify(IntentField.CHILDREN, "Сколько лет детям? От возраста зависят цена и размещение.");
        }
        if (!ages.isEmpty() && unknownAges == 0) {
            ctx.explicit.add(IntentField.CHILDREN);
        }

        if (adults == null && partySize != null) {
            adults = Math.max(1, partySize - ages.size());
        }
        if (adults != null) {
            ctx.explicit.add(IntentField.ADULTS);
        } else {
            adults = DEFAULT_ADULTS;
            ctx.assume(IntentField.ADULTS, "Число гостей не указано — считаем " + DEFAULT_ADULTS + " взрослых");
        }
        return new Guests(Math.max(1, Math.min(10, adults)), ages);
    }

    // ---------------------------------------------------------------- helpers

    private static <E> Optional<E> firstMatch(WorkText text, Map<E, Pattern> patterns) {
        for (Map.Entry<E, Pattern> e : patterns.entrySet()) {
            Matcher m = e.getValue().matcher(text.value());
            if (m.find()) {
                text.consume(m.start(), m.end());
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> Set<E> allMatches(WorkText text, Map<E, Pattern> patterns, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        for (Map.Entry<E, Pattern> e : patterns.entrySet()) {
            Matcher m = e.getValue().matcher(text.value());
            if (m.find()) {
                result.add(e.getKey());
                text.consume(m.start(), m.end());
            }
        }
        return result;
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, FLAGS);
    }

    private static <K, V> Map<K, V> linkedMap(Object... keyValues) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked") K key = (K) keyValues[i];
            @SuppressWarnings("unchecked") V value = (V) keyValues[i + 1];
            map.put(key, value);
        }
        return map;
    }

    /** Накопитель метаданных разбора. */
    private static final class Ctx {
        private final Set<IntentField> explicit = EnumSet.noneOf(IntentField.class);
        private final List<Note> assumptions = new ArrayList<>();
        private final List<Note> clarifications = new ArrayList<>();

        <T> T track(IntentField field, T value) {
            if (value != null) {
                explicit.add(field);
            }
            return value;
        }

        void assume(IntentField field, String text) {
            assumptions.add(new Note(field, text));
        }

        void clarify(IntentField field, String text) {
            clarifications.add(new Note(field, text));
        }
    }

    /**
     * Рабочая копия текста: распознанные фрагменты заменяются пробелами той же длины,
     * поэтому индексы остаются стабильными.
     */
    private static final class WorkText {
        private final StringBuilder buffer;

        WorkText(String text) {
            this.buffer = new StringBuilder(text);
        }

        String value() {
            return buffer.toString();
        }

        void consume(int start, int end) {
            for (int i = start; i < end; i++) {
                buffer.setCharAt(i, ' ');
            }
        }
    }
}
