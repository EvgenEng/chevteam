package ru.chevteam.onestep.intent.llm;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.intent.TransportPreference;
import ru.chevteam.onestep.intent.rules.IntentField;
import ru.chevteam.onestep.intent.rules.Note;
import ru.chevteam.onestep.intent.rules.RuleAnalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Слияние ответа LLM с детерминированным разбором — «AI с ограждениями».
 * <p>
 * Правила:
 * <ol>
 *   <li>Поле, найденное правилами в тексте <b>явно</b>, не перезаписывается — регулярное выражение
 *       по словам пользователя надёжнее генерации.</li>
 *   <li>Поле, которое правила лишь <b>предположили</b> (значение по умолчанию), заменяется значением LLM,
 *       если оно проходит валидацию; соответствующее допущение снимается.</li>
 *   <li>Множества (удобства, пожелания) объединяются.</li>
 *   <li>Невалидное значение LLM молча отбрасывается (а не роняет запрос).</li>
 * </ol>
 */
public final class IntentMerger {

    private IntentMerger() {
    }

    public static ExtractionResult merge(LlmIntentPayload llm, RuleAnalysis rules, LocalDate today) {
        SearchIntent base = rules.intent();
        Set<IntentField> overridden = EnumSet.noneOf(IntentField.class);

        Destination destination = pick(rules, IntentField.DESTINATION, base.destination(),
                () -> safe(() -> llm.country() == null || llm.country().isBlank() ? null
                        : new Destination(llm.country().strip(), blankToNull(llm.city()))), overridden);

        YearMonth month = pick(rules, IntentField.MONTH, base.stay().month(),
                () -> safe(() -> llm.month() == null ? null : nearestFuture(llm.month(), today)), overridden);
        LocalDate checkIn = pick(rules, IntentField.CHECK_IN, base.stay().checkIn(),
                () -> safe(() -> parseFutureDate(llm.checkIn(), today)), overridden);
        Integer nights = pick(rules, IntentField.NIGHTS, base.stay().nights(),
                () -> inRange(llm.nights(), 1, 60), overridden);
        StayPeriod stay = safeOr(() -> new StayPeriod(month, checkIn, nights), base.stay());

        Integer adults = pick(rules, IntentField.ADULTS, base.guests().adults(),
                () -> inRange(llm.adults(), 1, 10), overridden);
        List<Integer> childrenAges = pick(rules, IntentField.CHILDREN, base.guests().childrenAges(),
                () -> validAges(llm.childrenAges()), overridden);
        Guests guests = safeOr(() -> new Guests(adults, childrenAges), base.guests());

        Budget budget = pick(rules, IntentField.BUDGET, base.budget(), () -> safe(() -> llm.budget() == null ? null
                : new Budget(BigDecimal.valueOf(llm.budget()),
                "PER_NIGHT".equalsIgnoreCase(llm.budgetScope()) ? Budget.Scope.PER_NIGHT : Budget.Scope.TOTAL)),
                overridden);
        Integer minStars = pick(rules, IntentField.STARS, base.minStars(),
                () -> inRange(llm.minStars(), 1, 5), overridden);
        MealPlan mealPlan = pick(rules, IntentField.MEAL_PLAN, base.mealPlan(),
                () -> enumOrNull(MealPlan.class, llm.mealPlan()), overridden);
        TransportPreference transport = pick(rules, IntentField.TRANSPORT, base.transport(),
                () -> llm.originCity() == null && llm.directFlightPreferred() == null ? null
                        : new TransportPreference(blankToNull(llm.originCity()),
                        Boolean.TRUE.equals(llm.directFlightPreferred())), overridden);

        Set<Amenity> amenities = union(base.amenities(), llm.amenities(), Amenity.class);
        Set<SoftPreference> soft = union(base.softPreferences(), llm.softPreferences(), SoftPreference.class);
        Set<LocationFeature> location = union(base.locationFeatures(), llm.locationFeatures(), LocationFeature.class);
        List<ProximityConstraint> proximity = mergeProximity(base.proximity(), llm.landmarks());

        SearchIntent merged = new SearchIntent(destination, stay, guests, budget, minStars, mealPlan,
                amenities, soft, location, proximity, transport);

        List<String> assumptions = texts(rules.assumptions(), overridden, Set.of());
        Set<IntentField> answered = EnumSet.noneOf(IntentField.class);
        if (destination != null) {
            answered.add(IntentField.DESTINATION);
        }
        if (month != null) {
            answered.add(IntentField.MONTH);
        }
        List<String> clarifications = texts(rules.clarifications(), overridden, answered);
        return new ExtractionResult(merged, IntentSource.AI, assumptions, clarifications);
    }

    // ------------------------------------------------------------------ helpers

    private static <T> T pick(RuleAnalysis rules, IntentField field, T ruleValue, Supplier<T> llmValue,
                              Set<IntentField> overridden) {
        if (rules.isExplicit(field)) {
            return ruleValue;
        }
        T candidate = llmValue.get();
        if (candidate == null) {
            return ruleValue;
        }
        overridden.add(field);
        return candidate;
    }

    private static List<String> texts(List<Note> notes, Set<IntentField> overridden, Set<IntentField> answered) {
        return notes.stream()
                .filter(n -> !overridden.contains(n.field()) && !answered.contains(n.field()))
                .map(Note::text)
                .toList();
    }

    private static <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> T safeOr(Supplier<T> supplier, T fallback) {
        T value = safe(supplier);
        return value != null ? value : fallback;
    }

    private static Integer inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max ? value : null;
    }

    private static List<Integer> validAges(List<Integer> ages) {
        if (ages == null || ages.isEmpty()) {
            return null;
        }
        List<Integer> valid = ages.stream().filter(a -> a != null && a >= 0 && a <= 17).toList();
        return valid.size() == ages.size() ? valid : null;
    }

    private static YearMonth nearestFuture(int month, LocalDate today) {
        YearMonth candidate = YearMonth.of(today.getYear(), month);
        return candidate.isBefore(YearMonth.from(today)) ? candidate.plusYears(1) : candidate;
    }

    private static LocalDate parseFutureDate(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.strip());
            return date.isBefore(today) || date.isAfter(today.plusYears(2)) ? null : date;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> Set<E> union(Set<E> base, List<String> extra, Class<E> type) {
        Set<E> result = base.isEmpty() ? EnumSet.noneOf(type) : EnumSet.copyOf(base);
        if (extra != null) {
            for (String value : extra) {
                E e = enumOrNull(type, value);
                if (e != null) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    private static List<ProximityConstraint> mergeProximity(List<ProximityConstraint> base,
                                                            List<LlmIntentPayload.Landmark> llm) {
        Map<String, ProximityConstraint> byLandmark = new LinkedHashMap<>();
        base.forEach(p -> byLandmark.put(stem(p.landmark()), p));
        if (llm != null) {
            for (LlmIntentPayload.Landmark l : llm) {
                if (l == null || l.name() == null || l.name().isBlank()) {
                    continue;
                }
                int minutes = l.maxWalkMinutes() != null && l.maxWalkMinutes() > 0 && l.maxWalkMinutes() <= 120
                        ? l.maxWalkMinutes() : 15;
                ProximityConstraint candidate = safe(() -> new ProximityConstraint(l.name(), minutes));
                if (candidate != null) {
                    byLandmark.putIfAbsent(stem(candidate.landmark()), candidate);
                }
            }
        }
        return new ArrayList<>(byLandmark.values());
    }

    /** Грубая основа слова для дедупликации «эрмитажа» / «эрмитаж»: первые 5 букв. */
    private static String stem(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.length() > 5 ? w.substring(0, 5) : w;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
