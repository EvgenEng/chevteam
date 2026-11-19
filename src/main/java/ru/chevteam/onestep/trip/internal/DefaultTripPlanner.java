package ru.chevteam.onestep.trip.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.HotelDetails;
import ru.chevteam.onestep.catalog.RateQuote;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.search.HotelMatch;
import ru.chevteam.onestep.search.HotelSearch;
import ru.chevteam.onestep.search.MatchReason;
import ru.chevteam.onestep.search.SearchResponse;
import ru.chevteam.onestep.shared.error.BusinessRuleException;
import ru.chevteam.onestep.shared.error.NotFoundException;
import ru.chevteam.onestep.trip.TransportMode;
import ru.chevteam.onestep.trip.TripOption;
import ru.chevteam.onestep.trip.TripOption.Check;
import ru.chevteam.onestep.trip.TripOption.CostLine;
import ru.chevteam.onestep.trip.TripOption.Leg;
import ru.chevteam.onestep.trip.TripOption.Route;
import ru.chevteam.onestep.trip.TripOption.Stay;
import ru.chevteam.onestep.trip.TripPlan;
import ru.chevteam.onestep.trip.TripPlanner;
import ru.chevteam.onestep.trip.TripQuote;
import ru.chevteam.onestep.trip.TripRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class DefaultTripPlanner implements TripPlanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultTripPlanner.class);
    private static final int OPTIONS = 3;
    private static final int DEFAULT_NIGHTS = 7;
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", RU);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm", RU);
    private static final String HOTEL_ONLY_ANSWER = "Только отель";

    private final HotelSearch search;
    private final HotelCatalog catalog;
    private final Clock clock;

    DefaultTripPlanner(HotelSearch search, HotelCatalog catalog, Clock clock) {
        this.search = search;
        this.catalog = catalog;
        this.clock = clock;
    }

    // =================================================================== разговор

    @Override
    public TripPlan plan(TripRequest request) {
        TripText.Parsed text = TripText.read(request.text());
        if (text.searchText().isBlank()) {
            return ask(null, "DESTINATION", "Куда хочется?",
                    "Расскажите о поездке: куда, когда, кто едет и какой бюджет. Соберу отель, дорогу и трансферы.",
                    List.of("Ялта у моря", "Алушта на неделю", "Сочи на выходные"));
        }
        SearchResponse found = search.search(text.searchText());
        SearchIntent intent = found.understood().intent();
        List<String> chips = new ArrayList<>(found.understood().summary());

        if (intent.destination() == null) {
            return ask(chips, "DESTINATION", "Уточним одну деталь", "Куда хочется поехать? Можно город или регион.",
                    List.of("Ялта", "Алушта", "Сочи"));
        }

        // откуда едем
        boolean hotelOnly = text.hotelOnly() || HOTEL_ONLY_ANSWER.equalsIgnoreCase(strip(request.origin()));
        Places.City origin = null;
        if (!hotelOnly) {
            origin = Places.origin(request.origin()).orElse(text.origin());
            if (origin == null) {
                boolean unknown = request.origin() != null && !request.origin().isBlank() || text.unknownOrigin() != null;
                List<String> answers = new ArrayList<>(Places.POPULAR);
                answers.add(HOTEL_ONLY_ANSWER);
                return ask(chips, "ORIGIN", "Уточним одну деталь",
                        unknown ? "Не нашёл этот город в демо-справочнике дорог. Выберите ближайший крупный город"
                                + " или напишите «только отель»."
                                : "Учёл ваши пожелания. Из какого города отправляетесь?",
                        answers);
            }
            chips.add("Из " + origin.genitive());
        }

        // бюджет на всю поездку и резерв
        BigDecimal budget = tripBudget(intent.budget());
        BigDecimal reserve = TripText.reserveAnswer(request.reserve()).orElse(text.reserve());
        if (budget != null && reserve == null) {
            return ask(chips, "RESERVE", "Уточним одну деталь",
                    "Учёл ваши пожелания. Сколько из общего бюджета оставить на питание и личные расходы? "
                            + "Можно написать «резерв 0 рублей».",
                    List.of("Без отдельного резерва", "Оставить 10 000 ₽", "Оставить 20 000 ₽"));
        }
        if (reserve == null) {
            reserve = BigDecimal.ZERO;
        }
        if (budget != null) {
            chips.add("На весь отдых: " + rub(budget));
            if (reserve.signum() > 0) {
                chips.add("Другие расходы: " + rub(reserve));
            }
        }

        // даты
        List<String> assumptions = new ArrayList<>(found.understood().assumptions());
        LocalDate today = LocalDate.now(clock);
        int nights = intent.stay().nights() != null ? intent.stay().nights() : DEFAULT_NIGHTS;
        LocalDate checkIn = intent.stay().checkIn();
        if (checkIn == null) {
            checkIn = defaultCheckIn(intent.stay().month(), today);
            assumptions.add("Точные даты не названы — считаем заезд " + checkIn.format(DAY) + ", " + nights + " ноч.");
        }
        TransportMode wanted = request.transport() != null ? request.transport() : text.transport();

        List<HotelMatch> hotels = found.hotels().stream()
                .filter(h -> !request.excludeHotels().contains(h.hotelId().toString()))
                .toList();
        List<TripPlan.Option> priced = new ArrayList<>();
        for (HotelMatch h : hotels) {
            Optional<HotelDetails> details = catalog.findById(h.hotelId());
            if (details.isEmpty()) {
                continue;
            }
            Optional<TripOption> trip = build(details.get(), h.region(), h.rateId(), origin, wanted, checkIn, nights,
                    intent.guests().adults(), intent.guests().childrenAges(), budget, reserve);
            trip.ifPresent(t -> priced.add(new TripPlan.Option(t, h)));
        }

        TripPlan.Context context = new TripPlan.Context(intent.destination().displayName(),
                origin == null ? null : origin.name(), wanted, checkIn, nights, intent.guests().adults(),
                intent.guests().childrenAges(), budget, reserve, found.understood().summary());

        if (priced.isEmpty()) {
            return new TripPlan("EMPTY", "Не нашёл отелей под эти условия. Попробуйте смягчить требования или другие даты.",
                    null, chips, context, List.of(), found.totalCandidates(), assumptions, found.notice());
        }

        List<TripPlan.Option> fitting = priced.stream().filter(o -> !Boolean.FALSE.equals(o.trip().fits())).toList();
        List<TripPlan.Option> shown;
        String notice = found.notice();
        if (!fitting.isEmpty()) {
            shown = fitting.stream()
                    .sorted(Comparator.comparingInt((TripPlan.Option o) -> o.hotel().matchPercent()).reversed()
                            .thenComparing(o -> o.trip().total()))
                    .limit(OPTIONS).toList();
        } else {
            shown = priced.stream().sorted(Comparator.comparing(o -> o.trip().total())).limit(OPTIONS).toList();
            notice = "В бюджет " + rub(budget.subtract(reserve)) + " целиком не уложился ни один вариант. "
                    + "Показываю самые близкие — можно сдвинуть даты, уменьшить резерв или выбрать другой транспорт.";
        }
        String message = summary(found.totalCandidates(), fitting.size(), shown.get(0), budget != null);
        log.info("trip plan: destination={} origin={} compared={} fitting={} shown={}",
                intent.destination().displayName(), context.origin(), found.totalCandidates(), fitting.size(), shown.size());
        return new TripPlan(fitting.isEmpty() && budget != null ? "OVER_BUDGET" : "OPTIONS", message, null, chips,
                context, shown, found.totalCandidates(), assumptions, notice);
    }

    @Override
    public TripOption quote(TripQuote q) {
        HotelDetails hotel = catalog.findById(q.hotelId())
                .orElseThrow(() -> new NotFoundException("Отель не найден: " + q.hotelId()));
        Places.City origin = Places.origin(q.origin()).orElse(null);
        if (q.nights() < 1 || q.nights() > 30) {
            throw new BusinessRuleException("Ночей должно быть от 1 до 30");
        }
        if (q.adults() < 1 || q.adults() > 10) {
            throw new BusinessRuleException("Взрослых должно быть от 1 до 10");
        }
        return build(hotel, null, null, origin, q.transport(), q.checkIn(), q.nights(), q.adults(), q.childrenAges(),
                q.budget(), q.reserve())
                .orElseThrow(() -> new BusinessRuleException("В этом отеле нет номеров на столько гостей"));
    }

    // =================================================================== расчёт поездки

    private Optional<TripOption> build(HotelDetails hotel, String region, UUID preferredRate, Places.City origin,
                                       TransportMode wanted, LocalDate checkIn, int nights, int adults,
                                       List<Integer> kids, BigDecimal budget, BigDecimal reserve) {
        Optional<Stay> stay = stay(hotel, preferredRate, checkIn, nights, adults, kids.size());
        if (stay.isEmpty()) {
            return Optional.empty();
        }
        LocalDate checkOut = checkIn.plusDays(nights);
        Places.Point point = new Places.Point(hotel.lat(), hotel.lon());
        Places.Arrival arrival = Places.arrival(hotel.country(), region, hotel.city(), point);

        Map<TransportMode, TravelModel.Computed> computed = new EnumMap<>(TransportMode.class);
        TravelModel.Computed chosen = null;
        if (origin != null) {
            for (TransportMode mode : TransportMode.values()) {
                computed.put(mode, TravelModel.compute(mode, origin, arrival, point, hotel.name(), checkIn, checkOut,
                        adults, kids));
            }
            int people = adults + kids.size();
            TravelModel.Computed preferred = wanted == null ? null : computed.get(wanted);
            chosen = preferred != null && preferred.available() ? preferred
                    : computed.values().stream().filter(TravelModel.Computed::available)
                    .min(Comparator.comparingDouble(c -> TravelModel.burden(c, people))).orElse(null);
        }

        BigDecimal stayTotal = stay.get().total();
        BigDecimal travel = chosen == null ? BigDecimal.ZERO : chosen.travel();
        BigDecimal total = stayTotal.add(travel);
        BigDecimal spendable = budget == null ? null : budget.subtract(reserve);
        Boolean fits = spendable == null ? null : total.compareTo(spendable) <= 0;
        BigDecimal left = spendable == null ? null : spendable.subtract(total);

        List<Route> routes = new ArrayList<>();
        for (TravelModel.Computed c : computed.values()) {
            BigDecimal tripTotal = c.available() ? stayTotal.add(c.travel()) : null;
            routes.add(new Route(c.mode(), c.mode().title(), c.available(), c.reason(), c.hub(), c.roundTrip(),
                    c.transfers(), c.outMinutes(), c.backMinutes(), c.pros(), c.caveat(), tripTotal,
                    tripTotal == null ? null : tripTotal.subtract(total), c == chosen, c.nightLeg(), c.cars(), c.childSeats()));
        }
        Route route = routes.stream().filter(Route::selected).findFirst().orElse(null);

        List<CostLine> costs = new ArrayList<>();
        costs.add(new CostLine("STAY", "Проживание" + (stay.get().rooms() > 1 ? " · " + stay.get().rooms() + " номера" : ""),
                stayTotal));
        if (chosen != null) {
            BigDecimal road = chosen.roundTrip().subtract(chosen.overnight());
            costs.add(new CostLine("TRAVEL", chosen.mode() == TransportMode.CAR
                    ? "Бензин и платные дороги туда-обратно" : "Дорога туда-обратно · " + chosen.mode().title().toLowerCase(RU), road));
            if (chosen.overnight().signum() > 0) {
                costs.add(new CostLine("OVERNIGHT", "Ночёвка в пути туда и обратно", chosen.overnight()));
            }
            if (chosen.transfers().signum() > 0) {
                costs.add(new CostLine("TRANSFERS", "Оба трансфера", chosen.transfers()));
            }
        }

        Leg stayLeg = new Leg("STAY", "Проживание", null, hotel.name(), checkIn.atTime(TravelModel.CHECK_IN),
                checkOut.atTime(TravelModel.CHECK_OUT),
                stay.get().roomName() + (stay.get().rooms() > 1 ? " × " + stay.get().rooms() : ""));
        List<Leg> timeline = chosen == null ? List.of(stayLeg) : TravelModel.concat(chosen.out(), stayLeg, chosen.back());

        List<String> notes = new ArrayList<>();
        if (origin != null && arrival.note() != null) {
            notes.add(arrival.note());
        }
        String summary;
        String travelNote;
        if (chosen == null) {
            summary = origin == null ? "Только отель — дорогу планируете сами" : "Не нашёл, как сюда доехать из " + origin.genitive();
            travelNote = null;
        } else {
            summary = chosen.mode() == TransportMode.CAR ? "Машина → отель → обратно"
                    : chosen.mode().title() + " → трансфер → отель → трансфер → обратно";
            travelNote = TravelModel.duration(chosen.outMinutes()) + " до отеля · "
                    + (chosen.nightLeg() ? "есть ночной участок" : "без ночного участка в расчёте");
        }

        List<Check> checks = checks(chosen, stay.get(), checkIn, checkOut, nights, kids, fits, left);
        return Optional.of(new TripOption(hotel.id(), hotel.name(), hotel.city(), checkIn, checkOut, nights, adults,
                List.copyOf(kids), origin == null ? null : origin.name(), stay.get(), route, routes, timeline, costs,
                total, budget, reserve, spendable, fits, left, checks, summary, travelNote, notes));
    }

    /** Самый выгодный способ разместить всех; тариф из поиска — если ему хватает столько же номеров. */
    private Optional<Stay> stay(HotelDetails hotel, UUID preferredRate, LocalDate checkIn, int nights,
                                int adults, int children) {
        Stay best = null;
        Stay preferred = null;
        for (HotelDetails.RoomRate rate : hotel.rates()) {
            int rooms = RoomPlan.roomsFor(rate, adults, children);
            if (rooms == 0) {
                continue;
            }
            Optional<RateQuote> quote = catalog.quote(rate.id(), checkIn, nights);
            if (quote.isEmpty()) {
                continue;
            }
            BigDecimal total = quote.get().total().multiply(BigDecimal.valueOf(rooms));
            Stay s = new Stay(rate.id(), rate.roomName(), rate.mealPlan(), rooms, rate.refundable(), total,
                    total.divide(BigDecimal.valueOf(nights), 0, java.math.RoundingMode.HALF_UP));
            if (rate.id().equals(preferredRate)) {
                preferred = s;
            }
            if (best == null || s.rooms() < best.rooms()
                    || s.rooms() == best.rooms() && s.total().compareTo(best.total()) < 0) {
                best = s;
            }
        }
        if (preferred != null && best != null && preferred.rooms() == best.rooms()) {
            return Optional.of(preferred);
        }
        return Optional.ofNullable(best);
    }

    private List<Check> checks(TravelModel.Computed c, Stay stay, LocalDate checkIn, LocalDate checkOut, int nights,
                               List<Integer> kids, Boolean fits, BigDecimal left) {
        List<Check> list = new ArrayList<>();
        list.add(new Check("OK", "Даты проживания согласованы: " + checkIn.format(DAY) + " — " + checkOut.format(DAY)
                + ", " + nights + " ноч."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (c != null) {
            list.add(c.departure().isAfter(now)
                    ? new Check("OK", "Расчётное отправление в будущем: " + c.departure().format(DAY_TIME) + ".")
                    : new Check("FAIL", "Расчётное отправление уже прошло — выберите даты позже."));
            LocalDateTime checkInAt = checkIn.atTime(TravelModel.CHECK_IN);
            if (c.hotelArrival().isAfter(checkIn.atTime(23, 0))) {
                list.add(new Check("ASK", "Прибытие в отель " + c.hotelArrival().format(DAY_TIME)
                        + " — поздний заезд, предупредите отель."));
            } else if (c.hotelArrival().isBefore(checkInAt.minusHours(2))) {
                list.add(new Check("ASK", "Прибытие в отель " + c.hotelArrival().format(DAY_TIME)
                        + " — раньше заселения в 14:00, ранний заезд уточните у отеля."));
            } else {
                list.add(new Check("OK", "Расчётное прибытие в отель " + c.hotelArrival().format(DAY_TIME) + "."));
            }
            list.add(c.mode() == TransportMode.CAR
                    ? new Check("OK", "Выезд из отеля " + c.hotelLeave().format(DAY_TIME) + ", дома — "
                    + c.homeArrival().format(DAY_TIME) + ".")
                    : new Check("OK", "Выезд из отеля " + c.hotelLeave().format(DAY_TIME) + "; запас перед отправлением "
                    + c.bufferMinutes() + " мин."));
            if (c.cars() > 1 || stay.rooms() > 1) {
                list.add(new Check("ASK", "Вместимость номеров и машин для трансфера нужно уточнить перед поездкой."));
            }
        } else if (stay.rooms() > 1) {
            list.add(new Check("ASK", "Нужно " + stay.rooms() + " номера — уточните, что их можно взять рядом."));
        }
        if (!kids.isEmpty()) {
            list.add(new Check("ASK", "Условия размещения детей"
                    + (c != null && c.childSeats() > 0 ? " и детские кресла" : "") + " нужно подтвердить."));
        }
        if (fits != null) {
            list.add(fits ? new Check("OK", "Поездка укладывается в общий бюджет, остаётся " + rub(left) + ".")
                    : new Check("FAIL", "Дороже бюджета на " + rub(left.negate()) + "."));
        }
        list.add(new Check("ASK", "Цены и время в пути — предварительный расчёт; сохранение плана не бронирует услуги."));
        return list;
    }

    // =================================================================== тексты

    private String summary(int compared, int fitting, TripPlan.Option top, boolean hasBudget) {
        StringBuilder sb = new StringBuilder("Проверил ваши условия. Сравнил ").append(compared).append(' ')
                .append(plural(compared, "отель", "отеля", "отелей"));
        if (hasBudget) {
            sb.append(fitting == 0 ? ", целиком в бюджет не укладывается ни один"
                    : ", в бюджет " + plural(fitting, "укладывается ", "укладываются ", "укладываются ") + fitting);
        }
        sb.append(fitting == 0 && hasBudget ? ". Показываю самые близкие по цене. " : ". Показываю лучшие по соответствию и цене. ")
                .append(top.hotel().name()).append(" — ");
        List<String> why = new ArrayList<>();
        if (top.trip().route() != null) {
            why.add(top.trip().route().title().toLowerCase(RU) + (top.trip().route().transfers().signum() > 0
                    ? " и оба трансфера включены" : " туда и обратно включены"));
        }
        top.hotel().reasons().stream().filter(r -> r.kind() == MatchReason.Kind.MATCH).limit(2)
                .forEach(r -> why.add(lowerFirst(r.text())));
        sb.append(String.join("; ", why)).append(". Стоимость предварительная.");
        return sb.toString();
    }

    private static TripPlan ask(List<String> chips, String field, String title, String text, List<String> answers) {
        return new TripPlan("ASK", text, new TripPlan.Question(field, title, text, answers), chips, null,
                List.of(), 0, List.of(), null);
    }

    private static BigDecimal tripBudget(Budget budget) {
        return budget != null && budget.scope() == Budget.Scope.TOTAL ? budget.amount() : null;
    }

    private static LocalDate defaultCheckIn(YearMonth month, LocalDate today) {
        if (month == null) {
            return today.plusDays(14);
        }
        LocalDate candidate = month.atDay(10);
        return candidate.isAfter(today.plusDays(2)) ? candidate : today.plusDays(7);
    }

    static String rub(BigDecimal amount) {
        return String.format(RU, "%,d ₽", amount.longValue()).replace(' ', ' ').replace(',', ' ');
    }

    private static String plural(int n, String one, String few, String many) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        return mod10 == 1 ? one : mod10 >= 2 && mod10 <= 4 ? few : many;
    }

    private static String lowerFirst(String s) {
        return s == null || s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
