package ru.chevteam.onestep.trip.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.HotelDetails;
import ru.chevteam.onestep.catalog.MealAddonPrice;
import ru.chevteam.onestep.catalog.RateQuote;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.HotelMatch;
import ru.chevteam.onestep.search.HotelSearch;
import ru.chevteam.onestep.search.HotelServices;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.PriceQuote;
import ru.chevteam.onestep.search.SearchResponse;
import ru.chevteam.onestep.trip.TransportMode;
import ru.chevteam.onestep.trip.TripOption;
import ru.chevteam.onestep.trip.TripPlan;
import ru.chevteam.onestep.trip.TripQuote;
import ru.chevteam.onestep.trip.TripRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сценарий из демо: гость пишет поездку, отвечает на два вопроса, получает варианты и меняет гостей.
 * Поиск отелей — заглушка: разбор запроса покрыт тестами модуля intent, здесь важна сборка поездки.
 */
class DefaultTripPlannerTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Europe/Moscow"));
    private static final String TEXT = "В Ялту, 12-17 декабря 2026, трое взрослых, до 150 тысяч рублей на дорогу, отель и трансферы. Нужен бассейн.";

    private static final UUID OREANDA = UUID.fromString("288db926-584b-5572-a652-ae0a0657baa7");
    private static final UUID PALACE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID LUX = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private static final Map<UUID, HotelDetails> HOTELS = Map.of(
            OREANDA, hotel(OREANDA, "Ореанда", 44.4893, 34.1616,
                    rate("c18a4290-7e34-553e-994e-cf4c2d5d38ef", "Стандарт", 2, 1, 9000),
                    rate("4d2c5a17-471c-5a1b-a249-af12aff188b9", "Семейный", 3, 2, 12000)),
            PALACE, hotel(PALACE, "Палас", 44.4937, 34.1633,
                    rate("00000000-0000-0000-0000-0000000000b1", "Стандарт", 3, 1, 7000)),
            LUX, hotel(LUX, "Люкс у моря", 44.4950, 34.1700,
                    rate("00000000-0000-0000-0000-0000000000c1", "Люкс", 3, 1, 60000)));

    private final HotelSearch search = query -> {
        // так правила понимают текст из демо после того, как из него убраны слова о дороге
        assertThat(query).doesNotContainIgnoringCase("трансфер");
        SearchIntent intent = new SearchIntent(Destination.city("Россия", "Крым", "Ялта"),
                new StayPeriod(null, LocalDate.of(2026, 12, 12), 5), Guests.adults(3),
                query.contains("150 тысяч") ? Budget.total(150_000) : null, null, null, Set.of(Amenity.POOL),
                Set.of(), Set.of(), List.of(), null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, Set.of());
        return new SearchResponse(new SearchResponse.Understood(intent, IntentSource.RULES,
                List.of("Ялта", "12–17 дек.", "3 взр.", "Бассейн"), List.of(), List.of()),
                List.of(match(OREANDA, "Ореанда", 95), match(PALACE, "Палас", 90), match(LUX, "Люкс у моря", 99)),
                20, null);
    };
    private final HotelCatalog catalog = new HotelCatalog() {
        @Override
        public Optional<HotelDetails> findById(UUID id) {
            return Optional.ofNullable(HOTELS.get(id));
        }

        @Override
        public Optional<RateQuote> quote(UUID rateId, LocalDate checkIn, int nights) {
            return HOTELS.values().stream().flatMap(h -> h.rates().stream().map(r -> Map.entry(h, r)))
                    .filter(e -> e.getValue().id().equals(rateId)).findFirst()
                    .map(e -> new RateQuote(rateId, e.getKey().id(), e.getKey().name(), "Ялта", e.getValue().roomName(),
                            "BREAKFAST", e.getValue().maxAdults(), e.getValue().maxChildren(), true, nights,
                            e.getValue().basePricePerNight().multiply(BigDecimal.valueOf(nights)), "RUB"));
        }

        @Override
        public Optional<MealAddonPrice> mealAddon(UUID hotelId, String mealPlan) {
            return Optional.empty();
        }
    };
    private final DefaultTripPlanner planner = new DefaultTripPlanner(search, catalog, NOW);

    @Test
    void asksWhereFromThenReserveThenShowsTripsWithinBudget() {
        TripPlan first = planner.plan(new TripRequest(TEXT, null, null, null, null));
        assertThat(first.stage()).isEqualTo("ASK");
        assertThat(first.question().field()).isEqualTo("ORIGIN");
        assertThat(first.question().chips()).contains("Москва", "Только отель");

        TripPlan second = planner.plan(new TripRequest(TEXT, "Москва", null, null, null));
        assertThat(second.question().field()).isEqualTo("RESERVE");

        TripPlan plan = planner.plan(new TripRequest(TEXT, "Москва", "Резерв на личные расходы 20 тысяч рублей", null, null));
        assertThat(plan.stage()).isEqualTo("OPTIONS");
        assertThat(plan.chips()).contains("Из Москвы", "На весь отдых: 150 000 ₽", "Другие расходы: 20 000 ₽");
        assertThat(plan.options()).isNotEmpty().allSatisfy(o -> {
            TripOption t = o.trip();
            assertThat(t.fits()).isTrue();
            assertThat(t.total()).isLessThanOrEqualTo(new BigDecimal("130000"));
            assertThat(t.route().mode()).isNotEqualTo(TransportMode.PLANE); // в Крым не летают
            assertThat(t.timeline()).extracting(TripOption.Leg::kind)
                    .containsExactly("TRAVEL_OUT", "TRANSFER_IN", "STAY", "TRANSFER_OUT", "TRAVEL_BACK");
        });
        // дорогой «Люкс у моря» в бюджет не влезает, хотя совпадение у него самое высокое
        assertThat(plan.options()).extracting(o -> o.trip().hotelName()).doesNotContain("Люкс у моря");
        assertThat(plan.context().checkIn()).isEqualTo(LocalDate.of(2026, 12, 12));
        assertThat(plan.context().nights()).isEqualTo(5);
        assertThat(plan.message()).contains("Сравнил 20 отелей");
    }

    @Test
    void addingAChildRecalculatesRoomsTransfersAndBudget() {
        TripOption before = planner.quote(new TripQuote(PALACE, "Москва", TransportMode.TRAIN,
                LocalDate.of(2026, 12, 12), 5, 3, List.of(), new BigDecimal("150000"), new BigDecimal("20000")));
        TripOption after = planner.quote(new TripQuote(PALACE, "Москва", TransportMode.TRAIN,
                LocalDate.of(2026, 12, 10), 7, 3, List.of(7, 4), new BigDecimal("150000"), new BigDecimal("20000")));

        assertThat(before.stay().rooms()).isEqualTo(1);
        assertThat(after.stay().rooms()).isEqualTo(2);                  // 5 человек в номер на 3+1 не помещаются
        assertThat(after.route().cars()).isEqualTo(2);                   // и в одну машину трансфера тоже
        assertThat(after.route().childSeats()).isEqualTo(1);              // кресло нужно только 4-летнему
        assertThat(after.total()).isGreaterThan(before.total());
        assertThat(after.checks()).anyMatch(c -> c.text().contains("детские кресла"));
    }

    @Test
    void hotelOnlySkipsTheRoad() {
        TripPlan plan = planner.plan(new TripRequest("Ялта, 12-17 декабря 2026, трое взрослых, только отель", null, null, null, null));

        assertThat(plan.stage()).isEqualTo("OPTIONS");
        assertThat(plan.options().get(0).trip().route()).isNull();
        assertThat(plan.options().get(0).trip().timeline()).hasSize(1);
    }

    @Test
    void chosenTransportIsRespected() {
        TripOption car = planner.quote(new TripQuote(OREANDA, "Москва", TransportMode.CAR,
                LocalDate.of(2026, 12, 12), 5, 2, List.of(), null, null));

        assertThat(car.route().mode()).isEqualTo(TransportMode.CAR);
        assertThat(car.fits()).isNull();
        assertThat(car.routes()).anyMatch(r -> r.mode() == TransportMode.TRAIN && r.delta() != null);
        assertThat(car.routes()).anyMatch(r -> r.mode() == TransportMode.PLANE && !r.available());
    }

    // ------------------------------------------------------------------ данные

    private static HotelDetails.RoomRate rate(String id, String name, int adults, int children, int price) {
        return new HotelDetails.RoomRate(UUID.fromString(id), name, "BREAKFAST", adults, children, BigDecimal.valueOf(price), true);
    }

    private static HotelDetails hotel(UUID id, String name, double lat, double lon, HotelDetails.RoomRate... rates) {
        return new HotelDetails(id, name, "Россия", "Ялта", "Центр", "Ялта", 4, new BigDecimal("8.0"), 0, "", lat, lon,
                List.of(), null, List.of(rates));
    }

    private static HotelMatch match(UUID id, String name, int percent) {
        HotelDetails d = HOTELS.get(id);
        return new HotelMatch(id, name, "Россия", "Крым", "Ялта", "Центр", 4, new BigDecimal("8.0"), 0, "",
                new GeoPoint(d.lat(), d.lon()), percent,
                new PriceQuote(new BigDecimal("50000"), new BigDecimal("10000"), 5, "RUB", null, null),
                d.rates().get(0).id(), d.rates().get(0).roomName(), MealPlan.BREAKFAST, true,
                List.of(MatchReason.match("Бассейн в отеле")), List.of(), null, null, null, null, null, null, List.of(),
                HotelServices.EMPTY, "OSM", null);
    }
}
