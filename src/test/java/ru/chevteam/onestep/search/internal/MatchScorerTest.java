package ru.chevteam.onestep.search.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.PriceQuote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerTest {

    private static final SearchIntent FAMILY_TURKEY = new SearchIntent(null, new StayPeriod(null, null, 9),
            new Guests(2, List.of(7)), Budget.total(80_000), null, MealPlan.ALL_INCLUSIVE,
            Set.of(Amenity.AQUAPARK), Set.of(), Set.of(), List.of(), null);

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
                Set.of(), Set.of(SoftPreference.QUIET), Set.of(LocationFeature.CITY_CENTER), List.of(), null);
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

    private static PriceQuote price(long total, int nights) {
        return new PriceQuote(BigDecimal.valueOf(total), BigDecimal.valueOf(total / nights), nights, "RUB");
    }

    private static HotelCandidate hotel(Set<Amenity> amenities, String family, String rating,
                                        Integer beachLine, Integer landmarkDistance) {
        return new HotelCandidate(UUID.randomUUID(), "Test", "Турция", "Белек", null, 5, new BigDecimal(rating), 100,
                new GeoPoint(0, 0), beachLine, beachLine != null ? 50 : null, 500, 30,
                new BigDecimal("9.0"), new BigDecimal("9.0"), new BigDecimal("9.0"),
                family == null ? null : new BigDecimal(family), "desc", amenities,
                new HotelCandidate.Rate("Стандарт", MealPlan.ALL_INCLUSIVE, BigDecimal.valueOf(7400), true),
                BigDecimal.ONE, landmarkDistance, List.of());
    }
}
