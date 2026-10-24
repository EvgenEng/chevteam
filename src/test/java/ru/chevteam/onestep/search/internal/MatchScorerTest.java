package ru.chevteam.onestep.search.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.guide.BeachEntry;
import ru.chevteam.onestep.guide.Chance;
import ru.chevteam.onestep.guide.ViewingMode;
import ru.chevteam.onestep.guide.WildlifeInfo;
import ru.chevteam.onestep.guide.BeachInfo;
import ru.chevteam.onestep.guide.BeachSurface;
import ru.chevteam.onestep.guide.Comfort;
import ru.chevteam.onestep.guide.CrowdLevel;
import ru.chevteam.onestep.guide.EntryInfo;
import ru.chevteam.onestep.guide.EntryRegime;
import ru.chevteam.onestep.guide.HazardKind;
import ru.chevteam.onestep.guide.RainLevel;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.Severity;
import ru.chevteam.onestep.guide.TideLevel;
import ru.chevteam.onestep.guide.WaveLevel;
import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TransitKind;
import ru.chevteam.onestep.intent.Wildlife;
import ru.chevteam.onestep.search.AccessibilityInfo;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.HotelPolicies;
import ru.chevteam.onestep.search.HotelServices;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.NearbyPlace;
import ru.chevteam.onestep.search.PriceQuote;
import ru.chevteam.onestep.search.Surroundings;
import ru.chevteam.onestep.search.Terrain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerTest {

    private static final SearchIntent FAMILY_TURKEY = new SearchIntent(null, new StayPeriod(null, null, 9),
            new Guests(2, List.of(7)), Budget.total(80_000), null, MealPlan.ALL_INCLUSIVE,
            Set.of(Amenity.AQUAPARK), Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of());

    @Test
    void perfectHotelScoresHighWithReasons() {
        HotelCandidate hotel = hotel(Set.of(Amenity.AQUAPARK, Amenity.POOL), "9.4", "9.1", 1, null);
        MatchScorer.Scored scored = MatchScorer.score(FAMILY_TURKEY, hotel, price(66_600, 9), null);

        assertThat(scored.matchPercent()).isGreaterThanOrEqualTo(95);
        assertThat(scored.reasons()).extracting(MatchReason::kind).doesNotContain(MatchReason.Kind.MISS);
        assertThat(scored.reasons()).extracting(MatchReason::text).anyMatch(t -> t.startsWith("В бюджете"));
    }

    @Test
    void overBudgetAndMissingAmenityAreExplained() {
        HotelCandidate hotel = hotel(Set.of(Amenity.POOL), "7.5", "8.2", 1, null);
        MatchScorer.Scored scored = MatchScorer.score(FAMILY_TURKEY, hotel, price(100_000, 9), null);

        assertThat(scored.matchPercent()).isLessThan(70);
        assertThat(scored.reasons()).contains(
                MatchReason.miss("Дороже бюджета на 20 000 ₽"),
                MatchReason.partial("Аквапарка нет, есть бассейн"));
    }

    @Test
    void slightlyOverBudgetIsACompromiseNotAFailure() {
        HotelCandidate hotel = hotel(Set.of(Amenity.AQUAPARK), "9.2", "9.3", 1, null);
        MatchScorer.Scored scored = MatchScorer.score(FAMILY_TURKEY, hotel, price(84_000, 9), null);
        assertThat(scored.reasons()).extracting(MatchReason::kind).contains(MatchReason.Kind.PARTIAL);
    }

    @Test
    void proximityAndSoftPreferences() {
        SearchIntent spb = new SearchIntent(null, new StayPeriod(null, null, 2), Guests.adults(2), null, null, null,
                Set.of(), Set.of(SoftPreference.QUIET), Set.of(LocationFeature.CITY_CENTER), List.of(), null,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of());
        HotelCandidate near = hotel(Set.of(), null, "9.2", null, 433);
        HotelCandidate far = hotel(Set.of(), null, "9.2", null, 1274);

        MatchScorer.LandmarkRef hermitage = new MatchScorer.LandmarkRef("Эрмитаж", 15);
        MatchScorer.Scored nearScore = MatchScorer.score(spb, near, price(6_720, 2), hermitage);
        MatchScorer.Scored farScore = MatchScorer.score(spb, far, price(6_720, 2), hermitage);

        assertThat(nearScore.matchPercent()).isGreaterThan(farScore.matchPercent());
        assertThat(nearScore.reasons()).contains(MatchReason.match("≈7 мин пешком до: Эрмитаж"));
        assertThat(farScore.reasons()).contains(MatchReason.partial("≈20 мин пешком до: Эрмитаж"));
    }

    @Test
    void reasonsAreOrderedMatchesFirstInfoLast() {
        HotelCandidate hotel = hotel(Set.of(Amenity.POOL), "7.5", "8.2", 1, null);
        List<MatchReason> reasons = MatchScorer.score(FAMILY_TURKEY, hotel, price(100_000, 9), null).reasons();
        assertThat(reasons.get(0).kind()).isEqualTo(MatchReason.Kind.MATCH);
        assertThat(reasons.get(reasons.size() - 1).kind()).isEqualTo(MatchReason.Kind.INFO);
    }

    // ------------------------------------------------------------------ пляж, сезон, доступность

    private static final BeachInfo LAGOON_STRONG_TIDES = beach(BeachSurface.WHITE_SAND, BeachEntry.GENTLE, false,
            TideLevel.STRONG, "В отлив лагуна мелеет до колена", true, true, Map.of());
    private static final BeachInfo CALM_SAND = beach(BeachSurface.GOLDEN_SAND, BeachEntry.GENTLE, false,
            TideLevel.NONE, null, true, true, Map.of());
    private static final BeachInfo PEBBLE_DROP = beach(BeachSurface.COARSE_PEBBLE, BeachEntry.STEEP, true,
            TideLevel.NONE, null, false, false, Map.of());
    private static final BeachInfo SURF_SPOT = beach(BeachSurface.GOLDEN_SAND, BeachEntry.MODERATE, false,
            TideLevel.MODERATE, null, false, false, Map.of(BeachActivity.SURFING, List.of(4, 5, 6, 7, 8, 9, 10)));

    @Test
    void strongTidesPenalizeFamilyWithToddlerEvenIfNotAsked() {
        SearchIntent family = intent(new Guests(2, List.of(4)), Set.of(), Set.of(), Set.of());
        HotelCandidate hotel = hotel(Set.of(), "9.0", "9.0", 1, null);

        MatchScorer.Scored lagoon = MatchScorer.score(family, hotel, price(60_000, 7), null, ctx(LAGOON_STRONG_TIDES, null, 7, true));
        MatchScorer.Scored calm = MatchScorer.score(family, hotel, price(60_000, 7), null, ctx(CALM_SAND, null, 7, true));

        assertThat(calm.matchPercent()).isGreaterThan(lagoon.matchPercent());
        assertThat(lagoon.reasons()).contains(MatchReason.miss("В отлив лагуна мелеет до колена"));
    }

    @Test
    void sandyAndGentleEntryAreChecked() {
        SearchIntent wants = intent(Guests.adults(2), Set.of(BeachPreference.SANDY, BeachPreference.GENTLE_ENTRY), Set.of(), Set.of());
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);

        MatchScorer.Scored pebble = MatchScorer.score(wants, hotel, price(60_000, 7), null, ctx(PEBBLE_DROP, null, 7, true));
        assertThat(pebble.reasons()).contains(MatchReason.miss("Крупная галька — нужны коралловые тапочки"));
        assertThat(pebble.reasons()).extracting(MatchReason::kind).filteredOn(k -> k == MatchReason.Kind.MISS).hasSize(2);
    }

    @Test
    void activityOutOfSeasonIsACompromise() {
        SearchIntent surf = intent(Guests.adults(1), Set.of(), Set.of(BeachActivity.SURFING), Set.of());
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);

        assertThat(MatchScorer.score(surf, hotel, price(50_000, 7), null, ctx(SURF_SPOT, null, 7, true)).reasons())
                .contains(MatchReason.match("Серфинг — июль в сезоне"));
        assertThat(MatchScorer.score(surf, hotel, price(50_000, 7), null, ctx(SURF_SPOT, null, 1, true)).reasons())
                .contains(MatchReason.partial("Серфинг: лучший сезон — апрель — октябрь"));
        assertThat(MatchScorer.score(surf, hotel, price(50_000, 7), null, ctx(CALM_SAND, null, 7, true)).reasons())
                .contains(MatchReason.miss("Серфинг: пляж для этого не подходит"));
    }

    @Test
    void jellyfishSeasonIsExplained() {
        SearchIntent noJelly = intent(Guests.adults(2), Set.of(BeachPreference.NO_JELLYFISH), Set.of(), Set.of());
        SeasonInfo august = new SeasonInfo(8, 29, 26, RainLevel.LOW, WaveLevel.CALM, Comfort.COMFORT, null,
                List.of(new SeasonInfo.SeasonalHazard(HazardKind.JELLYFISH, Severity.MEDIUM, "Медузы-корнероты у берега")));
        SeasonInfo june = new SeasonInfo(6, 26, 22, RainLevel.LOW, WaveLevel.CALM, Comfort.COMFORT, null, List.of());
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);

        MatchScorer.Scored aug = MatchScorer.score(noJelly, hotel, price(50_000, 7), null, ctx(CALM_SAND, august, 8, true));
        MatchScorer.Scored jun = MatchScorer.score(noJelly, hotel, price(50_000, 7), null, ctx(CALM_SAND, june, 6, true));

        assertThat(jun.matchPercent()).isGreaterThan(aug.matchPercent());
        assertThat(aug.reasons()).contains(MatchReason.partial("Август: медузы-корнероты у берега"));
        assertThat(jun.reasons()).contains(MatchReason.match("Июнь: медуз обычно нет"));
    }

    @Test
    void rainySeasonCountsOnlyWhenMonthIsNamed() {
        SearchIntent beach = intent(Guests.adults(2), Set.of(BeachPreference.SANDY), Set.of(), Set.of());
        SeasonInfo monsoon = new SeasonInfo(7, 30, 28, RainLevel.HEAVY, WaveLevel.HIGH, Comfort.RAINY, null, List.of());
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);

        int named = MatchScorer.score(beach, hotel, price(50_000, 7), null, ctx(CALM_SAND, monsoon, 7, true)).matchPercent();
        int assumed = MatchScorer.score(beach, hotel, price(50_000, 7), null, ctx(CALM_SAND, monsoon, 7, false)).matchPercent();

        assertThat(named).isLessThan(assumed);
    }

    @Test
    void wheelchairNeedsAccessibleRoomFlatAreaAndBeach() {
        SearchIntent wheelchair = intent(Guests.adults(2), Set.of(), Set.of(), Set.of(AccessNeed.WHEELCHAIR));
        HotelCandidate accessible = hotel(Set.of(), null, "9.0", 1, null);
        HotelCandidate hillside = withAccess(accessible, new AccessibilityInfo(false, false, false, Terrain.STEEP,
                "К пляжу — 800 ступеней"));

        MatchScorer.Scored good = MatchScorer.score(wheelchair, accessible, price(50_000, 7), null, ctx(CALM_SAND, null, 7, true));
        MatchScorer.Scored bad = MatchScorer.score(wheelchair, hillside, price(50_000, 7), null, ctx(PEBBLE_DROP, null, 7, true));

        assertThat(good.matchPercent()).isGreaterThan(bad.matchPercent() + 30);
        assertThat(good.reasons()).contains(MatchReason.match("Номер для гостей на коляске, вход без ступеней"),
                MatchReason.match("Район ровный, без подъёмов"));
        assertThat(bad.reasons()).contains(MatchReason.miss("Нет условий для гостей на коляске"),
                MatchReason.miss("Крутые подъёмы и лестницы вокруг отеля"),
                MatchReason.info("К пляжу — 800 ступеней"));
    }

    @Test
    void cityHotelWithSightsWithinWalkScoresHigher() {
        SearchIntent city = intent(Guests.adults(2), Set.of(), Set.of(), Set.of());
        HotelCandidate central = withNearby(hotel(Set.of(), null, "9.0", null, null), List.of(
                place(300), place(600), place(900)));
        HotelCandidate outskirts = withNearby(hotel(Set.of(), null, "9.0", null, null), List.of(place(2800)));

        MatchScorer.Scored c = MatchScorer.score(city, central, price(20_000, 2), null);
        MatchScorer.Scored o = MatchScorer.score(city, outskirts, price(20_000, 2), null);

        assertThat(c.matchPercent()).isGreaterThan(o.matchPercent());
        assertThat(c.reasons()).contains(MatchReason.match("3 места в 15 минутах пешком"));
        assertThat(o.reasons()).contains(MatchReason.partial("До ближайшего интересного места ≈44 мин пешком"));
    }

    @Test
    void monthRangesAreHumanReadable() {
        assertThat(MatchScorer.months(List.of(11, 12, 1, 2, 3, 4))).isEqualTo("ноябрь — апрель");
        assertThat(MatchScorer.months(List.of(1, 3))).isEqualTo("январь, март");
        assertThat(MatchScorer.months(List.of())).isEqualTo("круглый год");
        assertThat(MatchScorer.places(1)).isEqualTo("1 место");
        assertThat(MatchScorer.places(4)).isEqualTo("4 места");
        assertThat(MatchScorer.places(11)).isEqualTo("11 мест");
    }

    // ------------------------------------------------------------------ животные, удобства, визы

    private static final HotelPolicies POLICIES = new HotelPolicies("14:00", "12:00", null,
            new HotelPolicies.Pets(Set.of(PetKind.CAT, PetKind.SMALL_PET), BigDecimal.valueOf(1000), null),
            List.of(new HotelPolicies.Feature(RoomFeature.BABY_COT, true, "бесплатно"),
                    new HotelPolicies.Feature(RoomFeature.KITCHEN, false, null)));

    @Test
    void petsAreCheckedBySpecies() {
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", null, null);
        assertThat(MatchScorer.score(withPets(Set.of(PetKind.CAT)), hotel, price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Можно с кошкой, " + Money.format(BigDecimal.valueOf(1000)) + "/ночь"));
        assertThat(MatchScorer.score(withPets(Set.of(PetKind.CAT, PetKind.DOG)), hotel, price(20_000, 3), null).reasons())
                .contains(MatchReason.partial("Можно с кошкой, а с собакой — нет"));
        assertThat(MatchScorer.score(withPets(Set.of(PetKind.DOG)), hotel, price(20_000, 3), null).reasons())
                .contains(MatchReason.miss("Нельзя с собакой"));
    }

    @Test
    void roomFeaturesReportPresenceAndAbsence() {
        SearchIntent wants = new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(),
                Set.of(RoomFeature.BABY_COT, RoomFeature.KITCHEN), Set.of(), false, Set.of());
        assertThat(MatchScorer.score(wants, hotel(Set.of(), null, "9.0", null, null), price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Детская кроватка — бесплатно"), MatchReason.miss("Нет: кухня"));
    }

    @Test
    void visaFreeLimitIsEnforced() {
        EntryInfo thailand = new EntryInfo(EntryRegime.VISA_FREE, 30, false, "Без визы до 30 дней", null, null,
                java.time.LocalDate.of(2026, 10, 24));
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);
        MatchScorer.Context ctx = new MatchScorer.Context(null, null, null, 1, false, List.of(), thailand, List.of());

        assertThat(MatchScorer.score(intent(Guests.adults(2), Set.of(), Set.of(), Set.of()), hotel, price(400_000, 40), null, ctx)
                .reasons()).contains(MatchReason.miss("Без визы — до 30 дней, а поездка 41 день: понадобится виза или продление"));
        SearchIntent visaFree = new SearchIntent(null, new StayPeriod(null, null, 7), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), true, Set.of());
        assertThat(MatchScorer.score(visaFree, hotel, price(70_000, 7), null, ctx).reasons())
                .contains(MatchReason.match("Без визы до 30 дней"));
    }

    // ------------------------------------------------------------------ дельфины, дороги, шопинг

    private static final Surroundings QUIET_DOOR = new Surroundings(Surroundings.Level.LOW, null, Surroundings.CarAccess.DOOR, null);

    @Test
    void wildlifeDependsOnSeason() {
        SearchIntent dolphins = new SearchIntent(null, new StayPeriod(null, null, 7), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of(Wildlife.DOLPHINS));
        List<WildlifeInfo> crimea = List.of(new WildlifeInfo(Wildlife.DOLPHINS, ViewingMode.SHORE, List.of(5, 6, 7, 8, 9, 10),
                Chance.HIGH, "У скал видно с берега"));
        HotelCandidate hotel = hotel(Set.of(), null, "9.0", 1, null);

        assertThat(MatchScorer.score(dolphins, hotel, price(50_000, 7), null,
                new MatchScorer.Context(null, null, null, 8, true, List.of(), null, crimea)).reasons())
                .contains(MatchReason.match("Дельфины — с берега, почти всегда. У скал видно с берега"));
        assertThat(MatchScorer.score(dolphins, hotel, price(50_000, 7), null,
                new MatchScorer.Context(null, null, null, 1, true, List.of(), null, crimea)).reasons())
                .contains(MatchReason.partial("Дельфины — лучше май — октябрь, с берега"));
        assertThat(MatchScorer.score(dolphins, hotel, price(50_000, 7), null,
                new MatchScorer.Context(null, null, null, 8, true, List.of(), null, List.of())).reasons())
                .contains(MatchReason.miss("Дельфины: здесь не встречаются"));
    }

    @Test
    void busyRoadAndBoatOnlyAccessAreExplained() {
        SearchIntent wants = new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(LocationFeature.AWAY_FROM_TRAFFIC, LocationFeature.CAR_ACCESS), List.of(), null, Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), false, Set.of());
        HotelCandidate island = withSurroundings(hotel(Set.of(), null, "9.0", null, null),
                new Surroundings(Surroundings.Level.HIGH, "Мотобайки под окнами", Surroundings.CarAccess.BOAT, "Только паром"), null);

        assertThat(MatchScorer.score(wants, island, price(20_000, 3), null).reasons())
                .contains(MatchReason.miss("Мотобайки под окнами"), MatchReason.miss("Только паром"));
    }

    @Test
    void shoppingWithinWalk() {
        SearchIntent wants = new SearchIntent(null, new StayPeriod(null, null, 2), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(LocationFeature.SHOPPING), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of());
        HotelCandidate near = withSurroundings(hotel(Set.of(), null, "9.0", null, null), QUIET_DOOR,
                new NearbyPlace("Terminal 21", "SHOPPING", 60, 1, 2, "По надземному переходу"));
        HotelCandidate none = withSurroundings(hotel(Set.of(), null, "9.0", null, null), QUIET_DOOR, null);

        assertThat(MatchScorer.score(wants, near, price(20_000, 2), null).reasons())
                .contains(MatchReason.match("Terminal 21: 1 мин пешком. По надземному переходу"));
        assertThat(MatchScorer.score(wants, none, price(20_000, 2), null).reasons())
                .contains(MatchReason.miss("Крупных торговых центров рядом нет"));
    }

    @Test
    void dietIsCheckedInHotelThenInCafesNearby() {
        SearchIntent halal = withExtras(Set.of(DietNeed.HALAL), List.of(), null);
        HotelServices.FoodPlace chaikhana = new HotelServices.FoodPlace("Чайхана", 400, 5,
                Set.of(HotelServices.FoodPlace.Meal.DINNER), Set.of(DietNeed.HALAL), "узбекская", 1, null);
        HotelCandidate inHotel = withServices(base(), services(List.of(),
                new HotelServices.Dining(List.of("турецкая"), Set.of(DietNeed.HALAL), "мясо халяль"), List.of(), List.of()));
        HotelCandidate nearby = withServices(base(), services(List.of(),
                new HotelServices.Dining(List.of("европейская"), Set.of(DietNeed.VEGETARIAN), null), List.of(), List.of(chaikhana)));
        HotelCandidate none = withServices(base(), HotelServices.EMPTY);

        assertThat(MatchScorer.score(halal, inHotel, price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Халяль в ресторане отеля: мясо халяль"));
        assertThat(MatchScorer.score(halal, nearby, price(20_000, 3), null).reasons())
                .contains(MatchReason.partial("Халяль: в отеле нет, но рядом «Чайхана» — 5 мин пешком"));
        assertThat(MatchScorer.score(halal, none, price(20_000, 3), null).reasons())
                .contains(MatchReason.miss("Халяль: ни в отеле, ни рядом"));

        SearchIntent vegetarian = withExtras(Set.of(DietNeed.VEGETARIAN), List.of(), null);
        HotelCandidate vegan = withServices(base(), services(List.of(),
                new HotelServices.Dining(List.of(), Set.of(DietNeed.VEGAN), null), List.of(), List.of()));
        assertThat(MatchScorer.score(vegetarian, vegan, price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Вегетарианское меню в ресторане отеля"));
    }

    @Test
    void transitTimeAgainstLimit() {
        SearchIntent wants = withExtras(Set.of(), List.of(new TransitConstraint(TransitKind.AIRPORT, 40),
                new TransitConstraint(TransitKind.METRO, 10)), null);
        HotelCandidate city = withServices(base(), services(List.of(
                new HotelServices.TransitPoint(TransitKind.AIRPORT, "Аэропорт Пулково", 15_500, 35, HotelServices.TransitPoint.Mode.TAXI, null),
                new HotelServices.TransitPoint(TransitKind.METRO, "Метро «Адмиралтейская»", 300, 5, HotelServices.TransitPoint.Mode.WALK, null)),
                null, List.of(), List.of()));
        HotelCandidate crimea = withServices(base(), services(List.of(
                new HotelServices.TransitPoint(TransitKind.AIRPORT, "Аэропорт Краснодар", 400_000, 420, HotelServices.TransitPoint.Mode.TAXI, null)),
                null, List.of(), List.of()));

        MatchScorer.Scored good = MatchScorer.score(wants, city, price(20_000, 3), null);
        assertThat(good.reasons()).contains(MatchReason.match("Аэропорт Пулково: 16 км, ≈35 мин на такси"),
                MatchReason.match("Метро «Адмиралтейская»: 300 м, ≈5 мин пешком"));
        MatchScorer.Scored far = MatchScorer.score(wants, crimea, price(20_000, 3), null);
        assertThat(far.reasons()).contains(MatchReason.miss("Аэропорт Краснодар: 400 км, ≈7 ч на такси"),
                MatchReason.miss("Рядом нет метро"));
        assertThat(good.matchPercent()).isGreaterThan(far.matchPercent());
    }

    @Test
    void mealAddonIsIncludedInPrice() {
        SearchIntent halfBoard = new SearchIntent(null, new StayPeriod(null, null, 3), new Guests(2, List.of(2, 7)), null, null,
                MealPlan.HALF_BOARD, Set.of(), Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, Set.of());
        HotelCandidate breakfastOnly = withServices(base(), services(List.of(), null, List.of(
                new HotelServices.MealAddon(MealPlan.HALF_BOARD, new BigDecimal("1800"), new BigDecimal("900"), 4, null)), List.of()));
        HotelCandidate withRate = new HotelCandidate(breakfastOnly.id(), breakfastOnly.name(), breakfastOnly.country(), null,
                breakfastOnly.city(), null, 4, breakfastOnly.rating(), 10, breakfastOnly.location(), null, null, null, null,
                null, null, null, null, "desc", Set.of(),
                new HotelCandidate.Rate(UUID.randomUUID(), "Стандарт", MealPlan.BREAKFAST, new BigDecimal("5000"), true),
                BigDecimal.ONE, null, List.of(), breakfastOnly.accessibility(), null, null, POLICIES, QUIET_DOOR, null,
                breakfastOnly.services());

        PriceQuote quote = DefaultHotelSearch.priceWithMeal(halfBoard, withRate, 3);

        // 2 взрослых × 1 800 + ребёнок 7 лет × 900 (малыш до 4 лет бесплатно) = 4 500 ₽ в сутки × 3 ночи
        assertThat(quote.mealAddedTotal()).isEqualByComparingTo("13500");
        assertThat(quote.total()).isEqualByComparingTo("28500");
        assertThat(MatchScorer.score(halfBoard, withRate, quote, null).reasons())
                .contains(MatchReason.match("Полупансион за доплату " + Money.format(new BigDecimal("13500")) + " — уже в цене"));
    }

    @Test
    void facilityNoteExplainsAmenity() {
        SearchIntent moto = new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, null,
                Set.of(Amenity.MOTO_PARKING, Amenity.BAR), Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), false, Set.of());
        HotelCandidate h = withServices(base(), new HotelServices(List.of(), List.of(
                new HotelServices.Facility(Amenity.MOTO_PARKING, true, "Под навесом"),
                new HotelServices.Facility(Amenity.BAR, false, "Алкоголь в отеле не подают")), null, List.of(), List.of()));

        assertThat(MatchScorer.score(moto, h, price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Парковка для мотоцикла: под навесом"),
                        MatchReason.miss("Нет: бар: алкоголь в отеле не подают"));
    }

    @Test
    void cafesNearbyForBreakfastAndDinner() {
        SearchIntent wants = new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, null, Set.of(),
                Set.of(), Set.of(LocationFeature.FOOD_NEARBY), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of());
        HotelCandidate h = withServices(base(), services(List.of(), null, List.of(), List.of(
                new HotelServices.FoodPlace("Сырники", 200, 3, Set.of(HotelServices.FoodPlace.Meal.BREAKFAST), Set.of(), "завтраки", 1, null),
                new HotelServices.FoodPlace("Печь", 500, 7, Set.of(HotelServices.FoodPlace.Meal.DINNER), Set.of(), "русская", 2, null))));

        assertThat(MatchScorer.score(wants, h, price(20_000, 3), null).reasons())
                .contains(MatchReason.match("Рядом где позавтракать и поужинать: «Сырники» 3 мин, «Печь» 7 мин"));
        assertThat(MatchScorer.score(wants, withServices(base(), HotelServices.EMPTY), price(20_000, 3), null).reasons())
                .contains(MatchReason.miss("Кафе в пешей доступности нет"));
    }

    private static HotelCandidate base() {
        return hotel(Set.of(), null, "8.5", null, null);
    }

    private static HotelServices services(List<HotelServices.TransitPoint> transport, HotelServices.Dining dining,
                                          List<HotelServices.MealAddon> addons, List<HotelServices.FoodPlace> food) {
        return new HotelServices(transport, List.of(), dining, addons, food);
    }

    private static HotelCandidate withServices(HotelCandidate h, HotelServices services) {
        return new HotelCandidate(h.id(), h.name(), h.country(), h.region(), h.city(), h.district(), h.stars(), h.rating(),
                h.reviewsCount(), h.location(), h.beachLine(), h.distanceToBeachM(), h.distanceToCenterM(),
                h.airportTransferMin(), h.quietScore(), h.cleanScore(), h.wifiScore(), h.familyScore(), h.description(),
                h.amenities(), h.rate(), h.seasonCoefficient(), h.landmarkDistanceM(), h.nearby(), h.accessibility(),
                h.beachId(), h.beachDistanceM(), h.policies(), h.surroundings(), h.shopping(), services);
    }

    private static SearchIntent withExtras(Set<DietNeed> diet, List<TransitConstraint> transit, MealPlan meal) {
        return new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, meal, Set.of(), Set.of(),
                Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of(), diet, transit);
    }

    private static HotelCandidate withSurroundings(HotelCandidate h, Surroundings s, NearbyPlace shopping) {
        return new HotelCandidate(h.id(), h.name(), h.country(), h.region(), h.city(), h.district(), h.stars(), h.rating(),
                h.reviewsCount(), h.location(), h.beachLine(), h.distanceToBeachM(), h.distanceToCenterM(),
                h.airportTransferMin(), h.quietScore(), h.cleanScore(), h.wifiScore(), h.familyScore(), h.description(),
                h.amenities(), h.rate(), h.seasonCoefficient(), h.landmarkDistanceM(), h.nearby(), h.accessibility(),
                h.beachId(), h.beachDistanceM(), h.policies(), s, shopping, h.services());
    }

    private static SearchIntent withPets(Set<PetKind> pets) {
        return new SearchIntent(null, new StayPeriod(null, null, 3), Guests.adults(2), null, null, null, Set.of(), Set.of(),
                Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), pets, false, Set.of());
    }

    private static SearchIntent intent(Guests guests, Set<BeachPreference> beach, Set<BeachActivity> activities,
                                       Set<AccessNeed> access) {
        return new SearchIntent(null, new StayPeriod(null, null, 7), guests, null, null, null, Set.of(), Set.of(),
                Set.of(), List.of(), null, beach, activities, access, Set.of(), Set.of(), false, Set.of());
    }

    private static MatchScorer.Context ctx(BeachInfo beach, SeasonInfo season, int month, boolean explicit) {
        return new MatchScorer.Context(beach, season, month, explicit);
    }

    private static BeachInfo beach(BeachSurface surface, BeachEntry entry, boolean drop, TideLevel tides, String tideNote,
                                   boolean kids, boolean accessible, Map<BeachActivity, List<Integer>> seasons) {
        return new BeachInfo(UUID.randomUUID(), "Пляж", 0, 0, surface, false, entry, drop, null, tides, tideNote, 1000,
                CrowdLevel.MEDIUM, seasons.keySet(), seasons, List.of(), kids, kids, accessible, null, "desc");
    }

    private static NearbyPlace place(int meters) {
        return new NearbyPlace("Место " + meters, "SIGHT", meters, WalkingTime.minutes(meters), WalkingTime.slowMinutes(meters), null);
    }

    private static HotelCandidate withAccess(HotelCandidate h, AccessibilityInfo access) {
        return new HotelCandidate(h.id(), h.name(), h.country(), h.region(), h.city(), h.district(), h.stars(), h.rating(),
                h.reviewsCount(), h.location(), h.beachLine(), h.distanceToBeachM(), h.distanceToCenterM(),
                h.airportTransferMin(), h.quietScore(), h.cleanScore(), h.wifiScore(), h.familyScore(), h.description(),
                h.amenities(), h.rate(), h.seasonCoefficient(), h.landmarkDistanceM(), h.nearby(), access, h.beachId(),
                h.beachDistanceM(), h.policies(), h.surroundings(), h.shopping(), h.services());
    }

    private static HotelCandidate withNearby(HotelCandidate h, List<NearbyPlace> nearby) {
        return new HotelCandidate(h.id(), h.name(), h.country(), h.region(), h.city(), h.district(), h.stars(), h.rating(),
                h.reviewsCount(), h.location(), h.beachLine(), h.distanceToBeachM(), h.distanceToCenterM(),
                h.airportTransferMin(), h.quietScore(), h.cleanScore(), h.wifiScore(), h.familyScore(), h.description(),
                h.amenities(), h.rate(), h.seasonCoefficient(), h.landmarkDistanceM(), nearby, h.accessibility(),
                h.beachId(), h.beachDistanceM(), h.policies(), h.surroundings(), h.shopping(), h.services());
    }

    private static PriceQuote price(long total, int nights) {
        return new PriceQuote(BigDecimal.valueOf(total), BigDecimal.valueOf(total / nights), nights, "RUB", null, null);
    }

    private static HotelCandidate hotel(Set<Amenity> amenities, String family, String rating,
                                        Integer beachLine, Integer landmarkDistance) {
        return new HotelCandidate(UUID.randomUUID(), "Test", "Турция", null, "Белек", null, 5, new BigDecimal(rating), 100,
                new GeoPoint(0, 0), beachLine, beachLine != null ? 50 : null, 500, 30,
                new BigDecimal("9.0"), new BigDecimal("9.0"), new BigDecimal("9.0"),
                family == null ? null : new BigDecimal(family), "desc", amenities,
                new HotelCandidate.Rate(UUID.randomUUID(), "Стандарт", MealPlan.ALL_INCLUSIVE, BigDecimal.valueOf(7400), true),
                BigDecimal.ONE, landmarkDistance, List.of(),
                new AccessibilityInfo(true, true, true, Terrain.FLAT, null), null, null, POLICIES, QUIET_DOOR, null, null);
    }
}
