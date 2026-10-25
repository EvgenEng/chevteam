package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.HotelFact;
import ru.chevteam.onestep.enrichment.HotelFacts;
import ru.chevteam.onestep.guide.BeachEntry;
import ru.chevteam.onestep.guide.BeachInfo;
import ru.chevteam.onestep.guide.Chance;
import ru.chevteam.onestep.guide.CrowdLevel;
import ru.chevteam.onestep.guide.EntryInfo;
import ru.chevteam.onestep.guide.EntryRegime;
import ru.chevteam.onestep.guide.HazardKind;
import ru.chevteam.onestep.guide.RainLevel;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.Severity;
import ru.chevteam.onestep.guide.TideLevel;
import ru.chevteam.onestep.guide.WaveLevel;
import ru.chevteam.onestep.guide.WildlifeInfo;
import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TravelStyle;
import ru.chevteam.onestep.intent.Wildlife;
import ru.chevteam.onestep.search.AccessibilityInfo;
import ru.chevteam.onestep.search.HotelPolicies;
import ru.chevteam.onestep.search.HotelServices;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.NearbyPlace;
import ru.chevteam.onestep.search.PriceQuote;
import ru.chevteam.onestep.search.Surroundings;
import ru.chevteam.onestep.search.Terrain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Объяснимый скоринг: каждое требование пользователя имеет вес; отель «зарабатывает» долю веса
 * и получает человекочитаемую причину. Итог — процент совпадения 0..100.
 * <p>
 * Помимо явных требований учитываются и неочевидные риски: сильные отливы и обрыв глубины для семей
 * с малышами, рельеф для пожилых, сезон дождей и медузы в месяц поездки.
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

    static final double W_SANDY = 10;
    static final double W_ENTRY = 10;
    static final double W_TIDES = 8;
    static final double W_JELLYFISH = 10;
    static final double W_BEACH_FUN = 5;
    static final double W_CROWD = 5;
    static final double W_ACTIVITY = 12;
    static final double W_KIDS_BEACH = 10;
    static final double W_KIDS_TERRAIN = 4;
    static final double W_SEASON = 8;
    static final double W_HAZARD = 5;
    static final double W_ACCESS = 15;
    static final double W_TERRAIN = 6;
    static final double W_ACCESS_BEACH = 6;
    static final double W_WALK = 8;
    static final double W_ROOM = 8;
    static final double W_PETS = 20;
    static final double W_VISA = 15;
    static final double W_WILDLIFE = 12;
    static final double W_TRAFFIC = 10;
    static final double W_CAR = 8;
    static final double W_SHOPPING = 10;
    static final double W_DIET = 14;
    static final double W_TRANSIT = 12;
    static final double W_FOOD_NEARBY = 8;
    static final double W_VIEW = 12;

    static final int YOUNG_CHILD_AGE = 10;
    static final int STROLLER_AGE = 3;
    static final int ALT_BEACH_RADIUS_M = 5000;
    static final int WALK_MINUTES = 15;

    static final BigDecimal SOFT_GOOD = new BigDecimal("8.8");
    static final BigDecimal SOFT_OK = new BigDecimal("7.8");
    static final BigDecimal RATING_EXCELLENT = new BigDecimal("9.0");

    /** Вес подтверждённого заявления: меньше любого настоящего критерия — это доверие, а не замена им. */
    static final double W_CONFIRMED = 3;
    private static final Locale RU = Locale.forLanguageTag("ru");

    private MatchScorer() {
    }

    static Scored score(SearchIntent intent, HotelCandidate hotel, PriceQuote price, LandmarkRef landmark) {
        return score(intent, hotel, price, landmark, Context.NONE);
    }

    /**
     * @param landmark разрешённая достопримечательность из запроса или {@code null}
     * @param ctx      пляж, сезон и месяц поездки
     */
    static Scored score(SearchIntent intent, HotelCandidate hotel, PriceQuote price, LandmarkRef landmark, Context ctx) {
        Tally t = new Tally();

        if (intent.budget() != null) {
            scoreBudget(t, intent.budget().totalFor(price.nights()), price.total());
        }
        if (intent.mealPlan() != null) {
            scoreMeal(t, intent.mealPlan(), hotel, price);
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
        scoreBeach(t, intent, ctx);
        scoreSeason(t, intent, hotel, ctx);
        scoreAccess(t, intent, hotel, ctx);
        scoreWalkability(t, intent, hotel);
        scoreRoom(t, intent, hotel);
        scorePets(t, intent, hotel);
        scoreEntry(t, intent, price.nights(), ctx.entry());
        scoreWildlife(t, intent, ctx);
        scoreSurroundings(t, intent, hotel);
        scoreShopping(t, intent, hotel);
        scoreDiet(t, intent, hotel);
        scoreTransit(t, intent, hotel);
        scoreFoodNearby(t, intent, hotel);
        scoreVerified(t, intent, ctx);
        scoreView(t, intent, hotel, ctx);
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

    /** Тарифы уже отфильтрованы: питание либо входит в тариф, либо отель добавляет его за доплату (она уже в цене). */
    private static void scoreMeal(Tally t, MealPlan wanted, HotelCandidate hotel, PriceQuote price) {
        if (hotel.rate().mealPlan() == wanted || price.mealAdded() != wanted) {
            t.add(W_MEAL, 1, MatchReason.match(hotel.rate().mealPlan().title()));
            return;
        }
        t.add(W_MEAL, 0.95, MatchReason.match(wanted.title() + " за доплату " + Money.format(price.mealAddedTotal())
                + " — уже в цене"));
    }

    private static void scoreAmenity(Tally t, Amenity amenity, HotelCandidate hotel) {
        HotelServices.Facility facility = hotel.services().facility(amenity).orElse(null);
        if (facility != null && !facility.available()) {
            t.add(W_AMENITY, 0, MatchReason.miss("Нет: " + amenity.title().toLowerCase(RU) + note(facility.note())));
        } else if (hotel.amenities().contains(amenity) || facility != null) {
            t.add(W_AMENITY, 1, MatchReason.match(amenity.title() + note(facility == null ? null : facility.note())));
        } else if (amenity == Amenity.AQUAPARK && hotel.amenities().contains(Amenity.POOL)) {
            t.add(W_AMENITY, 0.3, MatchReason.partial("Аквапарка нет, есть бассейн"));
        } else {
            t.add(W_AMENITY, 0, MatchReason.miss("Нет: " + amenity.title().toLowerCase(RU)));
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
        if (landmark.farAway()) {
            int taxi = taxiMinutes(distanceM);
            t.add(W_PROXIMITY / 2, Math.max(0.2, 1 - taxi / 90.0), MatchReason.partial(landmark.name() + ": "
                    + Distance.format(distanceM) + ", ≈" + duration(taxi) + " на такси"));
            return;
        }
        String text = walk <= NearbyPlace.MAX_COMFORT_WALK_MINUTES * 2
                ? "≈" + walk + " мин пешком до: " + landmark.name()
                : landmark.name() + ": " + Distance.format(distanceM) + ", пешком далеко — ≈" + duration(taxiMinutes(distanceM)) + " на такси";
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
            if (preference == SoftPreference.QUIET && hotel.verifiedByMap()) {
                scoreQuietByMap(t, hotel);
            }
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

    /** Оценок гостей у отеля ещё нет — тишину оцениваем по дорогам на карте. */
    private static void scoreQuietByMap(Tally t, HotelCandidate hotel) {
        Surroundings s = hotel.surroundings();
        if (s == null) {
            return;
        }
        String why = s.roadNote() != null ? s.roadNote() : "";
        switch (s.roadNoise()) {
            case LOW -> t.add(W_SOFT, 1, MatchReason.match("Тихо: оживлённых улиц рядом нет (по карте)"));
            case MEDIUM -> t.add(W_SOFT, 0.5, MatchReason.partial("Слышно улицу: " + why + " (по карте)"));
            case HIGH -> t.add(W_SOFT, 0, MatchReason.miss("Шумно: " + why + " (по карте)"));
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

    // -------------------------------------------------------------------- beach

    private static void scoreBeach(Tally t, SearchIntent intent, Context ctx) {
        Set<BeachPreference> wanted = intent.beach();
        BeachInfo beach = ctx.beach();
        if (beach == null) {
            int asked = wanted.size() + intent.activities().size();
            if (asked > 0) {
                t.add(W_SANDY * asked, 0, MatchReason.miss("Рядом с отелем нет пляжа"));
            }
            return;
        }
        if (wanted.contains(BeachPreference.SEA)) {
            Integer toBeach = ctx.beachDistanceM();
            if (toBeach == null || toBeach <= 500) {
                t.add(W_SANDY, 1, MatchReason.match(beach.name() + (toBeach != null ? " — " + Distance.format(toBeach) : "")));
            } else {
                t.add(W_SANDY, toBeach <= 1500 ? 0.6 : 0.3, MatchReason.partial("До пляжа " + Distance.format(toBeach)));
            }
        }
        if (wanted.contains(BeachPreference.SANDY)) {
            String surface = beach.surface().title() + (beach.artificialSand() ? " (завезённый)" : "");
            if (beach.surface().sandy()) {
                t.add(W_SANDY, 1, MatchReason.match(surface));
            } else if (!beach.surface().rough()) {
                t.add(W_SANDY, 0.4, MatchReason.partial(surface));
            } else {
                t.add(W_SANDY, 0, MatchReason.miss(surface + " — нужны коралловые тапочки"));
            }
        }
        boolean youngKids = intent.guests().childrenAges().stream().anyMatch(age -> age <= YOUNG_CHILD_AGE);
        boolean slowPace = intent.needsSlowPace();
        if (wanted.contains(BeachPreference.GENTLE_ENTRY)) {
            scoreEntry(t, beach, W_ENTRY);
        } else if (slowPace) {
            scoreEntry(t, beach, W_ENTRY / 2);
        }
        if (wanted.contains(BeachPreference.NO_TIDES)) {
            scoreTides(t, beach, W_TIDES);
        } else if (youngKids && beach.tides() == TideLevel.STRONG) {
            scoreTides(t, beach, W_TIDES / 2);
        }
        if (youngKids && !wanted.contains(BeachPreference.GENTLE_ENTRY)) {
            if (beach.kidsFriendly() && beach.gentleForSwimmers()) {
                t.add(W_KIDS_BEACH, 1, MatchReason.match("Пляж удобен малышам: пологий вход"));
            } else {
                String why = beach.depthDrop() && beach.depthNote() != null ? beach.depthNote() : beach.entry().title();
                t.add(W_KIDS_BEACH, beach.kidsFriendly() ? 0.5 : 0, MatchReason.partial("С малышами осторожно: " + lower(why)));
            }
            boolean stroller = intent.guests().childrenAges().stream().anyMatch(age -> age <= STROLLER_AGE);
            if (stroller && !beach.seniorsFriendly() && beach.accessibilityNote() != null && !slowPace) {
                // длинные лестницы к морю одинаково тяжелы пожилым и с детской коляской
                t.add(W_KIDS_TERRAIN, 0.2, MatchReason.partial("С детской коляской к пляжу непросто: " + lower(beach.accessibilityNote())));
            }
        }
        if (wanted.contains(BeachPreference.BEACH_FUN)) {
            if (!beach.entertainment().isEmpty()) {
                t.add(W_BEACH_FUN, 1, MatchReason.match("На пляже: " + String.join(", ",
                        beach.entertainment().subList(0, Math.min(3, beach.entertainment().size())))));
            } else {
                t.add(W_BEACH_FUN, 0, MatchReason.miss("Развлечений на пляже нет — тихое место"));
            }
        }
        if (wanted.contains(BeachPreference.UNCROWDED)) {
            switch (beach.crowd()) {
                case LOW -> t.add(W_CROWD, 1, MatchReason.match("Немноголюдно даже в сезон"));
                case MEDIUM -> t.add(W_CROWD, 0.6, MatchReason.partial(CrowdLevel.MEDIUM.title()));
                case HIGH -> t.add(W_CROWD, 0, MatchReason.miss(CrowdLevel.HIGH.title()));
            }
        }
        for (BeachActivity activity : intent.activities()) {
            Boolean inSeason = beach.inSeason(activity, ctx.month());
            if (inSeason == null) {
                ctx.alternatives().stream().filter(alt -> alt.beach().activities().contains(activity))
                        .findFirst()
                        .ifPresentOrElse(alt -> {
                            boolean altSeason = Boolean.TRUE.equals(alt.beach().inSeason(activity, ctx.month()));
                            t.add(W_ACTIVITY, altSeason ? 0.7 : 0.3, MatchReason.partial(activity.title() + " — "
                                    + alt.beach().name() + " в " + Distance.format(alt.distanceMeters()) + " от отеля"
                                    + (altSeason ? "" : ", лучший сезон — " + months(alt.beach().activitySeasons().get(activity)))));
                        }, () -> t.add(W_ACTIVITY, 0, MatchReason.miss(activity.title() + ": пляж для этого не подходит")));
            } else if (inSeason) {
                boolean hasSeason = beach.activitySeasons().containsKey(activity);
                t.add(W_ACTIVITY, 1, MatchReason.match(activity.title()
                        + (hasSeason && ctx.monthExplicit() ? " — " + monthName(ctx.month()).toLowerCase(RU) + " в сезоне" : " — можно")));
            } else {
                t.add(W_ACTIVITY, 0.4, MatchReason.partial(activity.title() + ": лучший сезон — "
                        + months(beach.activitySeasons().get(activity))));
            }
        }
    }

    private static void scoreEntry(Tally t, BeachInfo beach, double weight) {
        if (beach.gentleForSwimmers()) {
            t.add(weight, 1, MatchReason.match("Пологий вход и мелководье"));
        } else if (beach.entry() == BeachEntry.MODERATE && !beach.depthDrop()) {
            t.add(weight, 0.5, MatchReason.partial("Уклон умеренный, глубина нарастает быстрее"));
        } else {
            t.add(weight, 0, MatchReason.miss(beach.depthNote() != null ? beach.depthNote() : beach.entry().title()));
        }
    }

    private static void scoreTides(Tally t, BeachInfo beach, double weight) {
        switch (beach.tides()) {
            case NONE -> t.add(weight, 1, MatchReason.match("Отливы почти незаметны"));
            case MODERATE -> t.add(weight, 0.6, MatchReason.partial(
                    beach.tideNote() != null ? beach.tideNote() : TideLevel.MODERATE.title()));
            case STRONG -> t.add(weight, 0, MatchReason.miss(
                    beach.tideNote() != null ? beach.tideNote() : TideLevel.STRONG.title()));
        }
    }

    // -------------------------------------------------------------------- season

    private static void scoreSeason(Tally t, SearchIntent intent, HotelCandidate hotel, Context ctx) {
        SeasonInfo season = ctx.season();
        if (season == null) {
            if (intent.beach().contains(BeachPreference.NO_JELLYFISH)) {
                t.info(MatchReason.info("Нет данных о медузах для этого места"));
            }
            return;
        }
        boolean beachTrip = intent.wantsBeach() || (ctx.beach() != null && hotel.beachLine() != null);
        String month = monthName(season.month());
        if (beachTrip) {
            String weather = month + ": воздух +" + season.airC() + (season.waterC() != null ? ", вода +" + season.waterC() : "");
            double weight = ctx.monthExplicit() ? W_SEASON : 0;
            switch (season.rain()) {
                case HEAVY -> t.add(weight, 0.3, MatchReason.partial(month + ": сезон дождей"));
                case MODERATE -> t.add(weight, 0.7, MatchReason.partial(weather + ", бывают дожди"));
                default -> t.add(weight, 1, MatchReason.match(weather + ", " + RainLevel.DRY.title().toLowerCase(RU)));
            }
            // волна региона доходит до открытых пляжей; бухты за рифом и пологие лагуны она обходит
            BeachInfo beach = ctx.beach();
            boolean exposed = beach != null
                    && (beach.activities().contains(BeachActivity.SURFING) || beach.entry() != BeachEntry.GENTLE);
            if (season.waves() == WaveLevel.HIGH && exposed && !intent.activities().contains(BeachActivity.SURFING)) {
                t.add(ctx.monthExplicit() ? 4 : 0, 0.3, MatchReason.partial(month + ": сильные волны"));
            }
            if (beach != null && season.waterC() != null && season.waterC() < 21) {
                t.add(ctx.monthExplicit() ? 4 : 0, 0.5, MatchReason.partial("Вода прохладная: +" + season.waterC()));
            }
        }
        boolean jellyfishAsked = intent.beach().contains(BeachPreference.NO_JELLYFISH);
        boolean jellyfishFound = false;
        for (SeasonInfo.SeasonalHazard hazard : season.hazards()) {
            if (hazard.kind().beachRelated() && !beachTrip) {
                continue;
            }
            String text = month + ": " + lower(hazard.note());
            if (hazard.kind() == HazardKind.JELLYFISH && jellyfishAsked) {
                jellyfishFound = true;
                double share = switch (hazard.severity()) {
                    case HIGH -> 0;
                    case MEDIUM -> 0.3;
                    case LOW -> 0.7;
                };
                t.add(W_JELLYFISH, share, share == 0 ? MatchReason.miss(text) : MatchReason.partial(text));
            } else if (hazard.severity() == Severity.HIGH) {
                t.add(ctx.monthExplicit() ? W_HAZARD : 0, 0.3, MatchReason.partial(text));
            } else if (hazard.severity() == Severity.MEDIUM) {
                t.info(MatchReason.info(text));
            }
        }
        if (jellyfishAsked && !jellyfishFound) {
            t.add(W_JELLYFISH, 1, MatchReason.match(month + ": медуз обычно нет"));
        }
    }

    // -------------------------------------------------------------------- accessibility

    private static void scoreAccess(Tally t, SearchIntent intent, HotelCandidate hotel, Context ctx) {
        Set<AccessNeed> needs = intent.access();
        if (needs.isEmpty()) {
            return;
        }
        AccessibilityInfo a = hotel.accessibility();
        if (a == null) {
            t.info(MatchReason.info("Нет данных о доступной среде — уточните у отеля"));
            return;
        }
        if (needs.contains(AccessNeed.WHEELCHAIR)) {
            if (a.accessibleRoom() && a.stepFree()) {
                t.add(W_ACCESS, 1, MatchReason.match("Номер для гостей на коляске, вход без ступеней"));
            } else if (a.stepFree() && a.elevator()) {
                t.add(W_ACCESS, 0.5, MatchReason.partial("Вход без ступеней и лифт, но специального номера нет"));
            } else {
                t.add(W_ACCESS, 0, MatchReason.miss("Нет условий для гостей на коляске"));
            }
        } else if (needs.contains(AccessNeed.LIMITED_MOBILITY) || needs.contains(AccessNeed.SENIORS)) {
            if (a.elevator() && a.stepFree()) {
                t.add(W_ACCESS, 1, MatchReason.match("Лифт и вход без ступеней"));
            } else if (a.elevator()) {
                t.add(W_ACCESS, 0.7, MatchReason.partial("Есть лифт, но на входе ступени"));
            } else {
                t.add(W_ACCESS, 0, MatchReason.miss("Лифта нет"));
            }
        }
        if (intent.needsSlowPace()) {
            Terrain terrain = a.terrain() == null ? Terrain.FLAT : a.terrain();
            switch (terrain) {
                case FLAT -> t.add(W_TERRAIN, 1, MatchReason.match("Район ровный, без подъёмов"));
                case HILLY -> t.add(W_TERRAIN, 0.4, MatchReason.partial("Район холмистый — будут подъёмы"));
                case STEEP -> t.add(W_TERRAIN, 0, MatchReason.miss("Крутые подъёмы и лестницы вокруг отеля"));
            }
            BeachInfo beach = ctx.beach();
            if (beach != null && (intent.wantsBeach() || hotel.beachLine() != null)) {
                if (needs.contains(AccessNeed.WHEELCHAIR)) {
                    t.add(W_ACCESS_BEACH, beach.accessible() ? 1 : 0, beach.accessible()
                            ? MatchReason.match("К воде можно на коляске" + note(beach.accessibilityNote()))
                            : MatchReason.miss("К воде на коляске не подъехать" + note(beach.accessibilityNote())));
                } else {
                    t.add(W_ACCESS_BEACH, beach.seniorsFriendly() ? 1 : 0.3, beach.seniorsFriendly()
                            ? MatchReason.match("Пляж удобен пожилым")
                            : MatchReason.partial("Пляж неудобен пожилым" + note(beach.accessibilityNote())));
                }
            }
        }
        if (needs.contains(AccessNeed.LOW_VISION)) {
            t.info(MatchReason.info("О сопровождении незрячих гостей данных нет — уточните у отеля"));
        }
        if (a.note() != null) {
            t.info(MatchReason.info(a.note()));
        }
    }

    // -------------------------------------------------------------------- walkability

    /** Городская поездка: чем больше интересного в 15 минутах пешком, тем лучше. */
    private static void scoreWalkability(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (hotel.beachLine() != null || intent.wantsBeach()) {
            return;
        }
        boolean slow = intent.needsSlowPace();
        long count = hotel.nearby().stream()
                .filter(p -> (slow ? p.slowWalkMinutes() : p.walkMinutes()) <= WALK_MINUTES)
                .count();
        String pace = slow ? " спокойным шагом" : " пешком";
        if (count >= 3) {
            t.add(W_WALK, 1, MatchReason.match(places(count) + " в " + WALK_MINUTES + " минутах" + pace));
        } else if (count >= 1) {
            t.add(W_WALK, 0.6, MatchReason.partial(places(count) + " в " + WALK_MINUTES + " минутах" + pace));
        } else if (!hotel.nearby().isEmpty()) {
            NearbyPlace first = hotel.nearby().get(0);
            t.add(W_WALK, 0.2, MatchReason.partial("До ближайшего интересного места ≈"
                    + (slow ? first.slowWalkMinutes() : first.walkMinutes()) + " мин" + pace));
        }
    }

    // -------------------------------------------------------------------- room, pets, entry

    private static void scoreRoom(Tally t, SearchIntent intent, HotelCandidate hotel) {
        for (RoomFeature wanted : intent.room()) {
            HotelPolicies.Feature f = hotel.policies() == null ? null : hotel.policies().feature(wanted).orElse(null);
            if (f == null) {
                t.info(MatchReason.info(wanted.title() + ": нет данных — уточните у отеля"));
            } else if (f.available()) {
                t.add(W_ROOM, 1, MatchReason.match(wanted.title() + (f.note() != null ? " — " + f.note() : "")));
            } else {
                t.add(W_ROOM, 0, MatchReason.miss("Нет: " + lower(wanted.title())));
            }
        }
    }

    private static void scorePets(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (intent.pets().isEmpty()) {
            return;
        }
        if (hotel.policies() == null) {
            t.add(W_PETS, 0.3, MatchReason.partial("Про животных отель пока не указал — уточните перед бронированием"));
            return;
        }
        HotelPolicies.Pets pets = hotel.policies().pets();
        if (pets == null || !pets.any()) {
            t.add(W_PETS, 0, MatchReason.miss("С животными нельзя"));
            return;
        }
        List<PetKind> wanted = intent.pets().stream().filter(p -> p != PetKind.OTHER).toList();
        String fee = pets.fee() == null ? "" : pets.fee().signum() == 0 ? " бесплатно" : ", " + Money.format(pets.fee()) + "/ночь";
        String note = pets.note() != null ? " (" + lower(pets.note()) + ")" : "";
        if (wanted.isEmpty()) {
            t.add(W_PETS, 1, MatchReason.match("Можно с питомцами: " + String.join(", ",
                    pets.allowed().stream().sorted().map(p -> lower(p.title())).toList()) + fee + note));
            return;
        }
        List<PetKind> ok = wanted.stream().filter(pets.allowed()::contains).toList();
        List<PetKind> no = wanted.stream().filter(p -> !pets.allowed().contains(p)).toList();
        if (no.isEmpty()) {
            t.add(W_PETS, 1, MatchReason.match("Можно " + String.join(" и ", ok.stream().map(PetKind::with).toList()) + fee + note));
        } else if (!ok.isEmpty()) {
            t.add(W_PETS, 0.5, MatchReason.partial("Можно " + String.join(" и ", ok.stream().map(PetKind::with).toList())
                    + ", а " + String.join(" и ", no.stream().map(PetKind::with).toList()) + " — нет"));
        } else {
            t.add(W_PETS, 0, MatchReason.miss("Нельзя " + String.join(" и ", no.stream().map(PetKind::with).toList())));
        }
    }

    private static void scoreEntry(Tally t, SearchIntent intent, int nights, EntryInfo entry) {
        if (entry == null) {
            if (intent.visaFreeOnly()) {
                t.info(MatchReason.info("Нет данных о визовом режиме — уточните на сайте МИД"));
            }
            return;
        }
        int days = nights + 1;
        if (entry.maxDays() != null && days > entry.maxDays() && entry.regime() != EntryRegime.DOMESTIC) {
            t.add(W_VISA, 0, MatchReason.miss("Без визы — до " + entry.maxDays() + " дней, а поездка " + days
                    + " " + daysWord(days) + ": понадобится виза или продление"));
            return;
        }
        if (intent.visaFreeOnly()) {
            double share = switch (entry.regime()) {
                case DOMESTIC, VISA_FREE -> 1;
                case PRE_AUTHORIZATION -> 0.7;
                case VISA_ON_ARRIVAL -> 0.5;
                case E_VISA -> 0.2;
                case VISA_REQUIRED -> 0;
            };
            MatchReason reason = share >= 1 ? MatchReason.match(entry.summary())
                    : share > 0 ? MatchReason.partial(entry.summary()) : MatchReason.miss(entry.summary());
            t.add(W_VISA, share, reason);
        } else if (entry.regime() == EntryRegime.VISA_REQUIRED || entry.regime() == EntryRegime.E_VISA) {
            t.info(MatchReason.info(entry.summary()));
        }
    }

    // -------------------------------------------------------------------- wildlife, roads, shopping

    private static void scoreWildlife(Tally t, SearchIntent intent, Context ctx) {
        for (Wildlife wanted : intent.wildlife()) {
            List<WildlifeInfo> options = ctx.wildlife().stream().filter(w -> w.kind() == wanted).toList();
            if (options.isEmpty()) {
                t.add(W_WILDLIFE, 0, MatchReason.miss(wanted.title() + ": здесь не встречаются"));
                continue;
            }
            WildlifeInfo inSeason = options.stream().filter(w -> w.inSeason(ctx.month())).findFirst().orElse(null);
            if (inSeason != null) {
                double share = inSeason.chance() == Chance.LOW ? 0.5 : 1;
                String text = wanted.title() + " — " + inSeason.viewing().title() + ", " + inSeason.chance().title()
                        + ". " + inSeason.note();
                t.add(W_WILDLIFE, share, share == 1 ? MatchReason.match(text) : MatchReason.partial(text));
            } else {
                WildlifeInfo best = options.get(0);
                t.add(W_WILDLIFE, 0.3, MatchReason.partial(wanted.title() + " — лучше " + months(best.months())
                        + ", " + best.viewing().title()));
            }
        }
    }

    private static void scoreSurroundings(Tally t, SearchIntent intent, HotelCandidate hotel) {
        Surroundings s = hotel.surroundings();
        if (s == null) {
            return;
        }
        boolean quietAsked = intent.locationFeatures().contains(LocationFeature.AWAY_FROM_TRAFFIC);
        if (quietAsked) {
            switch (s.roadNoise()) {
                case LOW -> t.add(W_TRAFFIC, 1, MatchReason.match(s.roadNote() != null ? s.roadNote() : "Вдали от оживлённых дорог"));
                case MEDIUM -> t.add(W_TRAFFIC, 0.5, MatchReason.partial(s.roadNote() != null ? s.roadNote() : "Городская улица рядом"));
                case HIGH -> t.add(W_TRAFFIC, 0, MatchReason.miss(s.roadNote() != null ? s.roadNote() : "Оживлённая дорога у отеля"));
            }
        } else if (s.roadNoise() == Surroundings.Level.HIGH && intent.softPreferences().contains(SoftPreference.QUIET)
                && !hotel.verifiedByMap()) {
            t.add(4, 0.2, MatchReason.partial(s.roadNote() != null ? s.roadNote() : "Оживлённая дорога у отеля"));
        }
        String note = s.accessNote();
        if (s.carAccess() == null) {
            if (intent.locationFeatures().contains(LocationFeature.CAR_ACCESS)) {
                t.add(W_CAR, 0.5, MatchReason.partial("Подъезд к входу: нет данных — уточните у отеля"));
            }
            return;
        }
        if (intent.locationFeatures().contains(LocationFeature.CAR_ACCESS)) {
            switch (s.carAccess()) {
                case DOOR -> t.add(W_CAR, 1, MatchReason.match("Машина подъедет к входу" + (note != null ? ". " + note : "")));
                case NEARBY -> t.add(W_CAR, 0.6, MatchReason.partial(note != null ? note : "Высадка рядом, пару минут пешком"));
                case PEDESTRIAN -> t.add(W_CAR, 0.3, MatchReason.partial(note != null ? note : "Пешеходная зона — машина не подъедет"));
                case BOAT -> t.add(W_CAR, 0, MatchReason.miss(note != null ? note : "Добраться можно только по воде"));
            }
        } else if (s.carAccess() != Surroundings.CarAccess.DOOR && note != null) {
            if (intent.needsSlowPace()) {
                t.add(4, 0.3, MatchReason.partial(note));
            } else {
                t.info(MatchReason.info(note));
            }
        }
    }

    private static void scoreShopping(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (!intent.locationFeatures().contains(LocationFeature.SHOPPING)) {
            return;
        }
        NearbyPlace mall = hotel.shopping();
        if (mall == null) {
            t.add(W_SHOPPING, 0, MatchReason.miss("Крупных торговых центров рядом нет"));
            return;
        }
        String note = mall.note() != null ? ". " + mall.note() : "";
        if (mall.walkMinutes() <= 10) {
            t.add(W_SHOPPING, 1, MatchReason.match(mall.name() + ": " + mall.walkMinutes() + " мин пешком" + note));
        } else if (mall.walkMinutes() <= NearbyPlace.MAX_COMFORT_WALK_MINUTES) {
            t.add(W_SHOPPING, 0.7, MatchReason.partial(mall.name() + ": " + mall.walkMinutes() + " мин пешком" + note));
        } else {
            t.add(W_SHOPPING, 0.4, MatchReason.partial(mall.name() + ": " + Distance.format(mall.distanceMeters()) + ", лучше на такси"));
        }
    }

    /** Халяль, кошерное, веганское: сначала кухня отеля, затем кафе в 15 минутах пешком. */
    private static void scoreDiet(Tally t, SearchIntent intent, HotelCandidate hotel) {
        HotelServices services = hotel.services();
        for (DietNeed need : intent.diet()) {
            if (services.serves(need)) {
                t.add(W_DIET, 1, MatchReason.match(need.title() + " в ресторане отеля" + note(dietNote(services.dining().note(), need))));
                continue;
            }
            HotelServices.FoodPlace cafe = services.foodFor(need, NearbyPlace.MAX_COMFORT_WALK_MINUTES).orElse(null);
            if (cafe != null) {
                t.add(W_DIET, 0.6, MatchReason.partial(need.title() + ": в отеле нет, но рядом " + quoted(cafe.name()) + " — "
                        + cafe.walkMinutes() + " мин пешком"));
            } else {
                t.add(W_DIET, 0, MatchReason.miss(need.title() + ": ни в отеле, ни рядом"));
            }
        }
    }

    /** Из пометок кухни («мясо халяль · кошерное по запросу») — только та, что про нужную диету. */
    private static String dietNote(String note, DietNeed need) {
        if (note == null) {
            return null;
        }
        String marker = switch (need) {
            case HALAL -> "халяль";
            case KOSHER -> "кошер";
            case HINDU -> "индийск";
            case DIETARY -> "диет";
            default -> null;
        };
        if (marker == null) {
            return null;
        }
        for (String part : note.split(" · ")) {
            if (part.toLowerCase(RU).contains(marker)) {
                return part;
            }
        }
        return null;
    }

    /** Имя в кавычках, если своих кавычек в нём ещё нет: «Пышечная», но Кофейня «Сырники». */
    static String quoted(String name) {
        return name.contains("«") ? name : "«" + name + "»";
    }

    /** «До аэропорта не больше 40 минут», «рядом с метро»: обычное время в пути без пробок. */
    private static void scoreTransit(Tally t, SearchIntent intent, HotelCandidate hotel) {
        for (TransitConstraint c : intent.transit()) {
            HotelServices.TransitPoint point = hotel.services().fastest(c.kind()).orElse(null);
            if (point == null) {
                t.add(W_TRANSIT, 0, MatchReason.miss("Рядом нет " + c.kind().genitive()));
                continue;
            }
            String text = point.name() + ": " + Distance.format(point.distanceMeters()) + ", ≈" + duration(point.minutes())
                    + " " + mode(point.mode());
            if (point.minutes() <= c.maxMinutes()) {
                t.add(W_TRANSIT, 1, MatchReason.match(text));
            } else if (point.minutes() <= c.maxMinutes() * 1.5) {
                t.add(W_TRANSIT, 0.5, MatchReason.partial(text));
            } else {
                t.add(W_TRANSIT, 0, MatchReason.miss(text));
            }
        }
    }

    /** Где позавтракать и поужинать в пешей доступности. */
    private static void scoreFoodNearby(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (!intent.locationFeatures().contains(LocationFeature.FOOD_NEARBY)) {
            return;
        }
        List<HotelServices.FoodPlace> close = hotel.services().foodNearby().stream()
                .filter(f -> f.walkMinutes() <= NearbyPlace.MAX_COMFORT_WALK_MINUTES)
                .toList();
        if (close.isEmpty()) {
            t.add(W_FOOD_NEARBY, 0, MatchReason.miss("Кафе в пешей доступности нет"));
            return;
        }
        String names = String.join(", ", close.stream().limit(2)
                .map(f -> quoted(f.name()) + " " + f.walkMinutes() + " мин").toList());
        boolean breakfast = close.stream().anyMatch(f -> f.meals().contains(HotelServices.FoodPlace.Meal.BREAKFAST));
        boolean dinner = close.stream().anyMatch(f -> f.meals().contains(HotelServices.FoodPlace.Meal.DINNER));
        if (breakfast && dinner) {
            t.add(W_FOOD_NEARBY, 1, MatchReason.match("Рядом где позавтракать и поужинать: " + names));
        } else if (!breakfast && !dinner) {
            // часы работы неизвестны (так бывает у кафе из OpenStreetMap) — не обещаем ни завтраков, ни ужинов
            t.add(W_FOOD_NEARBY, 0.8, MatchReason.match("Рядом где поесть: " + names));
        } else {
            t.add(W_FOOD_NEARBY, 0.6, MatchReason.partial("Рядом " + (breakfast ? "завтраки" : "ужины") + ": " + names));
        }
    }

    /** Такси по городу без пробок: дорога ≈ ×1,3 от прямой, 25 км/ч, плюс подача. */
    static int taxiMinutes(int meters) {
        return (int) Math.round(5 + meters * 1.3 / 1000 / 25 * 60);
    }

    /**
     * Факты, которые пайплайн сам нашёл на карте: лестницы к морю, стройки, бары под окнами — и расхождения
     * с заявлениями отеля. Минус показываем, только когда он важен этому гостю: коляска и пожилые — лестницы,
     * тишина — бары и стройки. Остальным это справка, а не приговор отелю.
     */
    private static void scoreVerified(Tally t, SearchIntent intent, Context ctx) {
        if (ctx.verified() == null) {
            return;
        }
        boolean stroller = intent.guests().childrenAges().stream().anyMatch(a -> a <= STROLLER_AGE);
        boolean slow = intent.needsSlowPace();
        boolean quiet = intent.softPreferences().contains(SoftPreference.QUIET)
                || intent.locationFeatures().contains(LocationFeature.AWAY_FROM_TRAFFIC);
        ctx.fact("CLIMB_TO_SEA").ifPresent(f -> {
            if ("STEEP".equals(f.level()) && (slow || stroller)) {
                t.add(W_TERRAIN, 0.2, MatchReason.partial("От моря в гору: " + f.value() + " (по карте высот)"));
            } else if ("MODERATE".equals(f.level()) && (slow || stroller)) {
                t.add(W_TERRAIN, 0.6, MatchReason.partial("От моря небольшой подъём: " + f.value() + " (по карте высот)"));
            } else if ("FLAT".equals(f.level()) && (slow || stroller) && intent.wantsBeach()) {
                t.add(W_TERRAIN, 1, MatchReason.match("К морю почти без подъёма (по карте высот)"));
            } else if ("STEEP".equals(f.level()) && intent.wantsBeach()) {
                t.info(MatchReason.info("От моря в гору: " + f.value()));
            }
        });
        ctx.fact(STAIRS).ifPresent(f -> {
            if ("MANY".equals(f.level()) && (slow || stroller)) {
                t.add(W_TERRAIN, 0.2, MatchReason.partial("К морю " + f.value() + " — " + (stroller ? "тяжело с коляской" : "тяжело при больных ногах")
                        + " (по карте)"));
            } else if ("NONE".equals(f.level()) && (slow || stroller) && intent.wantsBeach()) {
                t.add(W_TERRAIN, 1, MatchReason.match("К морю без лестниц (по карте)"));
            } else if ("MANY".equals(f.level()) && intent.wantsBeach()) {
                t.info(MatchReason.info("К морю " + f.value()));
            }
        });
        if (quiet || intent.travelStyle() == TravelStyle.FAMILY) {
            ctx.fact(NIGHTLIFE).filter(f -> "HIGH".equals(f.level()))
                    .ifPresent(f -> t.add(4, 0.3, MatchReason.partial("Рядом бары: " + lower(f.value()) + " (по карте)")));
            ctx.fact(CONSTRUCTION).filter(f -> "YES".equals(f.level()))
                    .ifPresent(f -> t.add(4, 0.3, MatchReason.partial("Рядом " + lower(f.value()) + " (по карте)")));
        }
        List<String> confirmed = new ArrayList<>();
        for (ClaimCheck check : ctx.verified().checks()) {
            if (check.status() == ClaimStatus.DISPUTED && "SEA_VIEW".equals(check.code())
                    && intent.locationFeatures().contains(LocationFeature.SEA_VIEW)) {
                t.add(5, 0.2, MatchReason.partial("Отель указывает «" + check.claimed() + "», но по рельефу: " + check.observed()));
                continue;
            }
            boolean relevant = switch (check.code()) {
                case "ROAD_NOISE", "NIGHTLIFE", "CONSTRUCTION" -> quiet;
                case "SEA_DISTANCE", "BEACH_SURFACE" -> intent.wantsBeach();
                case "STAIRS_TO_SEA", "CLIMB_TO_SEA" -> slow || stroller || intent.wantsBeach();
                case "SEA_VIEW" -> intent.locationFeatures().contains(LocationFeature.SEA_VIEW);
                default -> false;
            };
            if (check.status() == ClaimStatus.CONFIRMED && relevant) {
                confirmed.add(check.claimed());
                continue;
            }
            if (check.status() != ClaimStatus.DISPUTED) {
                continue;
            }
            MatchReason reason = new MatchReason(relevant ? MatchReason.Kind.PARTIAL : MatchReason.Kind.INFO,
                    "Отель указывает «" + check.claimed() + "», но по карте: " + check.observed());
            if (relevant) {
                t.add(5, 0.2, reason);
            } else {
                t.info(reason);
            }
        }
        if (!confirmed.isEmpty()) {
            // отель сам подтвердил то, что важно гостю, и карта с ним согласна — один небольшой плюс к доверию
            t.add(W_CONFIRMED, 1.0, MatchReason.match("Отель подтвердил, и карта согласна: " + String.join(", ", confirmed)));
        }
    }

    /**
     * «С видом на море», «закаты»: у реальных отелей — по линии видимости на карте высот,
     * у демо-карточек — по балкону «с видом на море» из данных отеля.
     */
    private static void scoreView(Tally t, SearchIntent intent, HotelCandidate hotel, Context ctx) {
        for (LocationFeature want : List.of(LocationFeature.SEA_VIEW, LocationFeature.SUNSET_VIEW)) {
            if (!intent.locationFeatures().contains(want)) {
                continue;
            }
            String code = want == LocationFeature.SEA_VIEW ? "SEA_VIEW" : "SUNSET_VIEW";
            Optional<HotelFact> fact = ctx.fact(code);
            if (fact.isPresent()) {
                HotelFact f = fact.get();
                switch (f.level()) {
                    case "YES" -> t.add(W_VIEW, Math.min(1, 0.7 + (f.number() == null ? 0 : f.number()) / 300),
                            MatchReason.match(cap(f.value()) + " (по карте высот)"));
                    case "PARTIAL" -> t.add(W_VIEW, 0.5, MatchReason.partial(cap(f.value()) + " (по карте высот)"));
                    default -> t.add(W_VIEW, 0, MatchReason.miss(cap(f.value()) + " (по карте высот)"));
                }
                continue;
            }
            boolean balcony = hotel.policies() != null && hotel.policies().features().stream()
                    .anyMatch(x -> x.available() && x.note() != null && x.note().contains("вид на море"));
            if (want == LocationFeature.SEA_VIEW && balcony) {
                t.add(W_VIEW, 0.9, MatchReason.match("Балкон с видом на море (по данным отеля)"));
            } else if (want == LocationFeature.SEA_VIEW && hotel.beachLine() != null && hotel.beachLine() == 1) {
                t.add(W_VIEW, 0.6, MatchReason.partial("Первая линия — вид на море вероятен, но отель его не подтвердил"));
            } else {
                t.add(W_VIEW, 0.2, MatchReason.partial(want.title() + ": отель не указал, проверить по рельефу нельзя"));
            }
        }
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final String STAIRS = "STAIRS_TO_SEA";
    private static final String NIGHTLIFE = "NIGHTLIFE";
    private static final String CONSTRUCTION = "CONSTRUCTION";

    /** «35 мин», «1 ч 20 мин», «2 ч 30 мин». */
    static String duration(int minutes) {
        if (minutes < 60) {
            return minutes + " мин";
        }
        int h = minutes / 60;
        int m = (int) (Math.round((minutes % 60) / 5.0) * 5);
        if (m == 60) {
            h++;
            m = 0;
        }
        return h + " ч" + (m > 0 ? " " + m + " мин" : "");
    }

    private static String mode(HotelServices.TransitPoint.Mode mode) {
        return switch (mode) {
            case WALK -> "пешком";
            case TAXI -> "на такси";
            case TRAIN -> "поездом";
            case BUS -> "на автобусе";
            case BOAT -> "на катере";
            case SHUTTLE -> "трансфером отеля";
        };
    }

    static String daysWord(int n) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        return mod10 == 1 && mod100 != 11 ? "день"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "дня" : "дней";
    }

    private static void scoreRating(Tally t, HotelCandidate hotel) {
        t.add(W_RATING, hotel.rating().doubleValue() / 10.0,
                hotel.rating().compareTo(RATING_EXCELLENT) >= 0
                        ? MatchReason.match("Отличный рейтинг " + hotel.rating().toPlainString()
                        + " (" + hotel.reviewsCount() + " отзывов)")
                        : null);
    }

    private static void addInfo(Tally t, SearchIntent intent, HotelCandidate hotel) {
        if (intent.transport() != null && intent.transit().isEmpty() && hotel.airportTransferMin() != null) {
            t.info(MatchReason.info("Трансфер из аэропорта ≈" + hotel.airportTransferMin() + " мин"));
        }
        if (hotel.rate().refundable()) {
            t.info(MatchReason.info("Бесплатная отмена"));
        }
    }

    // -------------------------------------------------------------------- text helpers

    static String monthName(int month) {
        String name = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, RU);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** «май — октябрь» для подряд идущих месяцев (в том числе через Новый год), иначе перечисление. */
    static String months(List<Integer> months) {
        if (months == null || months.isEmpty()) {
            return "круглый год";
        }
        if (months.size() == 12) {
            return "круглый год";
        }
        boolean consecutive = true;
        for (int i = 1; i < months.size(); i++) {
            if (months.get(i) != months.get(i - 1) % 12 + 1) {
                consecutive = false;
                break;
            }
        }
        if (consecutive && months.size() >= 3) {
            return monthName(months.get(0)).toLowerCase(RU) + " — " + monthName(months.get(months.size() - 1)).toLowerCase(RU);
        }
        return String.join(", ", months.stream().map(m -> monthName(m).toLowerCase(RU)).toList());
    }

    static String places(long n) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        String word = mod10 == 1 && mod100 != 11 ? "место"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "места" : "мест";
        return n + " " + word;
    }

    private static String lower(String s) {
        return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String note(String note) {
        return note == null ? "" : ": " + lower(note);
    }

    // -------------------------------------------------------------------- types

    record Scored(int matchPercent, List<MatchReason> reasons) {
    }

    /**
     * @param farAway пешком до места не дойти ни от одного отеля — оцениваем, насколько ближе ехать
     */
    record LandmarkRef(String name, int maxWalkMinutes, boolean farAway) {

        LandmarkRef(String name, int maxWalkMinutes) {
            this(name, maxWalkMinutes, false);
        }
    }

    /**
     * @param beach         ближайший пляж или {@code null}
     * @param beachDistanceM сколько идти до пляжа от отеля
     * @param season        погода и риски в месяц поездки или {@code null}
     * @param month         месяц поездки (названный или текущий)
     * @param monthExplicit месяц назван пользователем — тогда сезон влияет на оценку, иначе это справка
     * @param alternatives  другие пляжи города в пределах {@link #ALT_BEACH_RADIUS_M}
     * @param entry         правила въезда для граждан РФ или {@code null}
     * @param wildlife      морские обитатели этого места
     * @param verified      факты пайплайна и сверка заявлений отеля или {@code null}
     */
    record Context(BeachInfo beach, Integer beachDistanceM, SeasonInfo season, int month, boolean monthExplicit,
                   List<NearbyBeach> alternatives, EntryInfo entry, List<WildlifeInfo> wildlife, HotelFacts.Profile verified) {
        static final Context NONE = new Context(null, null, null, 1, false, List.of(), null, List.of(), null);

        Context(BeachInfo beach, SeasonInfo season, int month, boolean monthExplicit) {
            this(beach, null, season, month, monthExplicit, List.of(), null, List.of(), null);
        }

        Context(BeachInfo beach, Integer beachDistanceM, SeasonInfo season, int month, boolean monthExplicit,
                List<NearbyBeach> alternatives, EntryInfo entry, List<WildlifeInfo> wildlife) {
            this(beach, beachDistanceM, season, month, monthExplicit, alternatives, entry, wildlife, null);
        }

        Optional<HotelFact> fact(String code) {
            return verified == null ? Optional.empty()
                    : verified.facts().stream().filter(f -> f.code().equals(code)).findFirst();
        }

        Context {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
            wildlife = wildlife == null ? List.of() : List.copyOf(wildlife);
        }
    }

    /** Другой пляж того же города, отсортированный по расстоянию от отеля. */
    record NearbyBeach(BeachInfo beach, int distanceMeters) {
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
