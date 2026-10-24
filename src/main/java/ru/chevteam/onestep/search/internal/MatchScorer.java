package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.TravelStyle;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.PriceQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Объяснимый скоринг: каждое требование пользователя имеет вес; отель «зарабатывает» долю веса
 * и получает человекочитаемую причину. Итог — процент совпадения 0..100.
 * <p>
 * Чистая функция без зависимостей — тестируется таблично и детерминирована.
 */
final class MatchScorer {

    static final double W_BUDGET = 25;
    static final double W_MEAL = 15;
    static final double W_AMENITY = 10;
    static final double W_FIRST_LINE = 12;
    static final double W_CENTER = 10;
    static final double W_PROXIMITY = 15;
    static final double W_SOFT = 8;
    static final double W_STARS = 5;
    static final double W_FAMILY = 8;
    static final double W_RATING = 10;

    static final BigDecimal SOFT_GOOD = new BigDecimal("8.8");
    static final BigDecimal SOFT_OK = new BigDecimal("7.8");
    static final BigDecimal RATING_EXCELLENT = new BigDecimal("9.0");

    private MatchScorer() {
    }

    /**
     * @param landmark разрешённая достопримечательность из запроса или {@code null}
     */
    static Scored score(SearchIntent intent, HotelCandidate hotel, PriceQuote price, LandmarkRef landmark) {
        Tally t = new Tally();

        if (intent.budget() != null) {
            scoreBudget(t, intent.budget().totalFor(price.nights()), price.total());
        }
        if (intent.mealPlan() != null) {
            // тарифы уже отфильтрованы по питанию — сюда попадают только совпадения
            t.add(W_MEAL, 1, MatchReason.match(hotel.rate().mealPlan().title()));
        }
        if (intent.minStars() != null) {
            t.add(W_STARS, 1, MatchReason.match(hotel.stars() + "★"));
        }
        for (Amenity amenity : intent.amenities()) {
            scoreAmenity(t, amenity, hotel);
        }
        if (intent.locationFeatures().contains(LocationFeature.FIRST_LINE)) {
            scoreFirstLine(t, hotel);
        }
        if (intent.locationFeatures().contains(LocationFeature.CITY_CENTER)) {
            scoreCenter(t, hotel);
        }
        if (landmark != null && hotel.landmarkDistanceM() != null) {
            scoreProximity(t, landmark, hotel.landmarkDistanceM());
        }
        for (SoftPreference preference : intent.softPreferences()) {
            scoreSoft(t, preference, hotel);
        }
        if (intent.travelStyle() == TravelStyle.FAMILY) {
            scoreFamily(t, hotel);
        }
        scoreRating(t, hotel);
        addInfo(t, intent, hotel);

        return new Scored(t.percent(), t.reasons());
    }

    // -------------------------------------------------------------------- criteria

    private static void scoreBudget(Tally t, BigDecimal budget, BigDecimal total) {
        int cmp = total.compareTo(budget);
        if (cmp <= 0) {
            BigDecimal left = budget.subtract(total);
            t.add(W_BUDGET, 1, MatchReason.match("В бюджете: " + Money.format(total)
                    + (left.signum() > 0 ? " — остаётся " + Money.format(left) : "")));
            return;
        }
        BigDecimal over = total.subtract(budget);
        BigDecimal overShare = over.divide(budget, 4, RoundingMode.HALF_UP);
        if (overShare.compareTo(new BigDecimal("0.10")) <= 0) {
            t.add(W_BUDGET, 0.5, MatchReason.partial("Чуть дороже бюджета: +" + Money.format(over)));
        } else {
            t.add(W_BUDGET, 0, MatchReason.miss("Дороже бюджета на " + Money.format(over)));
        }
    }

    private static void scoreAmenity(Tally t, Amenity amenity, HotelCandidate hotel) {
        if (hotel.amenities().contains(amenity)) {
            t.add(W_AMENITY, 1, MatchReason.match(amenity.title()));
        } else if (amenity == Amenity.AQUAPARK && hotel.amenities().contains(Amenity.POOL)) {
            t.add(W_AMENITY, 0.3, MatchReason.partial("Аквапарка нет, есть бассейн"));
        } else {
            t.add(W_AMENITY, 0, MatchReason.miss("Нет: " + amenity.title().toLowerCase()));
        }
    }

    private static void scoreFirstLine(Tally t, HotelCandidate hotel) {
        Integer line = hotel.beachLine();
        Integer toBeach = hotel.distanceToBeachM();
        if (line != null && line == 1) {
            t.add(W_FIRST_LINE, 1, MatchReason.match("Первая линия" + (toBeach != null ? ", до пляжа " + toBeach + " м" : "")));
        } else if (toBeach != null && toBeach <= 300) {
            t.add(W_FIRST_LINE, 0.6, MatchReason.partial("Вторая линия, до пляжа " + toBeach + " м"));
        } else if (toBeach != null) {
            t.add(W_FIRST_LINE, 0, MatchReason.miss("До моря " + Distance.format(toBeach)));
        } else {
            t.add(W_FIRST_LINE, 0, MatchReason.miss("Не у моря"));
        }
    }

    private static void scoreCenter(Tally t, HotelCandidate hotel) {
        Integer d = hotel.distanceToCenterM();
        if (d == null) {
            return;
        }
        if (d <= 1000) {
            t.add(W_CENTER, 1, MatchReason.match("Центр — " + Distance.format(d) + " до главной площади"));
        } else if (d <= 2500) {
            t.add(W_CENTER, 0.5, MatchReason.partial("Недалеко от центра — " + Distance.format(d)));
        } else {
            t.add(W_CENTER, 0, MatchReason.miss("Далеко от центра — " + Distance.format(d)));
        }
    }

    private static void scoreProximity(Tally t, LandmarkRef landmark, int distanceM) {
        int walk = WalkingTime.minutes(distanceM);
        String text = "≈" + walk + " мин пешком до: " + landmark.name();
        if (walk <= landmark.maxWalkMinutes()) {
            t.add(W_PROXIMITY, 1, MatchReason.match(text));
        } else if (walk <= landmark.maxWalkMinutes() * 1.5) {
            t.add(W_PROXIMITY, 0.5, MatchReason.partial(text));
        } else {
            t.add(W_PROXIMITY, 0, MatchReason.miss(text));
        }
    }

    private static void scoreSoft(Tally t, SoftPreference preference, HotelCandidate hotel) {
        BigDecimal score = switch (preference) {
            case QUIET -> hotel.quietScore();
            case CLEAN -> hotel.cleanScore();
            case GOOD_WIFI -> hotel.wifiScore();
        };
        if (score == null) {
            return;
        }
        String label = preference.title() + " — " + score.toPlainString() + " по отзывам";
        if (score.compareTo(SOFT_GOOD) >= 0) {
            t.add(W_SOFT, 1, MatchReason.match(label));
        } else if (score.compareTo(SOFT_OK) >= 0) {
            t.add(W_SOFT, 0.5, MatchReason.partial(label));
        } else {
            t.add(W_SOFT, 0, MatchReason.miss(preference == SoftPreference.QUIET
                    ? "Бывает шумно — " + score.toPlainString() + " по отзывам" : label));
        }
    }

    private static void scoreFamily(Tally t, HotelCandidate hotel) {
        BigDecimal family = hotel.familyScore();
        if (family == null) {
            t.add(W_FAMILY, 0.3, MatchReason.info("Нет оценок от семей с детьми"));
        } else if (family.compareTo(SOFT_GOOD) >= 0) {
            t.add(W_FAMILY, 1, MatchReason.match("Семьи с детьми ставят " + family.toPlainString()));
        } else {
            t.add(W_FAMILY, family.doubleValue() / 10.0 * 0.6,
                    MatchReason.partial("Семьи с детьми ставят " + family.toPlainString()));
        }
    }

    private static void scoreRating(Tally t, HotelCandidate hotel) {
        t.add(W_RATING, hotel.rating().doubleValue() / 10.0,
                hotel.rating().compareTo(RATING_EXCELLENT) >= 0
                        ? MatchReason.match("Отличный рейтинг " + hotel.rating().toPlainString()
                        + " (" + hotel.reviewsCount() + " отзывов)")
                        : null);
    }

    private static void addInfo(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (intent.transport() != null && hotel.airportTransferMin() != null) {
            t.info(MatchReason.info("Трансфер из аэропорта ≈" + hotel.airportTransferMin() + " мин"));
        }
        if (hotel.rate().refundable()) {
            t.info(MatchReason.info("Бесплатная отмена"));
        }
    }

    // -------------------------------------------------------------------- types

    record Scored(int matchPercent, List<MatchReason> reasons) {
    }

    record LandmarkRef(String name, int maxWalkMinutes) {
    }

    private static final class Tally {
        private double earned;
        private double possible;
        private final List<MatchReason> matched = new ArrayList<>();
        private final List<MatchReason> other = new ArrayList<>();
        private final List<MatchReason> infos = new ArrayList<>();

        void add(double weight, double share, MatchReason reason) {
            possible += weight;
            earned += weight * Math.max(0, Math.min(1, share));
            if (reason != null) {
                (reason.kind() == MatchReason.Kind.MATCH ? matched : other).add(reason);
            }
        }

        void info(MatchReason reason) {
            infos.add(reason);
        }

        int percent() {
            return possible == 0 ? 0 : (int) Math.round(100.0 * earned / possible);
        }

        /** Сначала совпадения, потом компромиссы, в конце справка. */
        List<MatchReason> reasons() {
            List<MatchReason> all = new ArrayList<>(matched);
            other.sort((a, b) -> Integer.compare(order(a), order(b)));
            all.addAll(other);
            all.addAll(infos);
            return List.copyOf(all);
        }

        private static int order(MatchReason r) {
            return switch (r.kind()) {
                case MATCH -> 0;
                case PARTIAL -> 1;
                case MISS -> 2;
                case INFO -> 3;
            };
        }
    }
}
