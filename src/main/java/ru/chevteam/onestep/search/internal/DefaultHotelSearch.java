package ru.chevteam.onestep.search.internal;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.chevteam.onestep.catalog.Pricing;
import ru.chevteam.onestep.enrichment.HotelFacts;
import ru.chevteam.onestep.guide.BeachInfo;
import ru.chevteam.onestep.guide.EntryInfo;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.TravelGuide;
import ru.chevteam.onestep.guide.WildlifeInfo;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.search.BeachView;
import ru.chevteam.onestep.search.HotelMatch;
import ru.chevteam.onestep.search.HotelSearch;
import ru.chevteam.onestep.search.PriceQuote;
import ru.chevteam.onestep.search.SearchResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Оркестрация «одного шага»: понять запрос → выбрать кандидатов → посчитать цену → объяснимо ранжировать.
 */
@Service
class DefaultHotelSearch implements HotelSearch {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotelSearch.class);
    private static final int CANDIDATE_LIMIT = 300;
    /** Достопримечательность: допускаем кандидатов до 1.5× желаемого времени — чтобы показать компромиссы. */
    private static final double PROXIMITY_SLACK = 1.5;

    private final IntentExtractor intentExtractor;
    private final CandidateRepository candidates;
    private final TravelGuide guide;
    private final SearchProperties props;
    private final Clock clock;
    private final HotelFacts facts;

    DefaultHotelSearch(IntentExtractor intentExtractor, CandidateRepository candidates, TravelGuide guide,
                       SearchProperties props, Clock clock, HotelFacts facts) {
        this.intentExtractor = intentExtractor;
        this.candidates = candidates;
        this.guide = guide;
        this.props = props;
        this.clock = clock;
        this.facts = facts;
    }

    @Override
    @Timed(value = "onestep.search", description = "Полный цикл поиска по запросу")
    @Cacheable(cacheNames = SearchCacheConfig.CACHE, key = "#query.strip().toLowerCase()")
    public SearchResponse search(String query) {
        ExtractionResult extraction = intentExtractor.extract(query);
        SearchIntent intent = extraction.intent();
        List<String> assumptions = new ArrayList<>(extraction.assumptions());

        Optional<ResolvedLandmark> landmark = resolveLandmark(intent, assumptions);
        ProximityConstraint proximity = landmark.isPresent() ? intent.proximity().get(0) : null;

        int nights = intent.stay().nights() != null ? intent.stay().nights() : 7;
        boolean monthExplicit = intent.stay().month() != null;
        int month = monthExplicit
                ? intent.stay().month().getMonthValue()
                : LocalDate.now(clock).getMonthValue();

        Destination where = intent.destination();
        CandidateQuery candidateQuery = new CandidateQuery(
                where != null ? where.country() : null,
                where != null && where.city() == null ? where.region() : null,
                where != null ? where.city() : null,
                intent.minStars(),
                intent.guests().adults(),
                intent.guests().children(),
                intent.mealPlan(),
                month,
                landmark.map(ResolvedLandmark::id).orElse(null),
                proximity != null ? WalkingTime.radiusMeters(proximity.maxWalkMinutes() * PROXIMITY_SLACK) : null,
                CANDIDATE_LIMIT);

        List<HotelCandidate> found = candidates.find(candidateQuery);
        String notice = null;
        boolean relaxed = false;
        boolean farLandmark = false;
        if (found.isEmpty() && candidateQuery.radiusMeters() != null) {
            // в пешей доступности ничего — показываем ближайшие отели того же города, а не уезжаем в другую страну
            found = candidates.find(candidateQuery.withoutRadius());
            farLandmark = !found.isEmpty();
            if (farLandmark) {
                assumptions.add("В пешей доступности от «" + landmark.get().name() + "» отелей нет — показываем ближайшие");
            }
        }
        boolean groupTooBig = false;
        int guestsTotal = intent.guests().adults() + intent.guests().children();
        if (found.isEmpty() && where != null && guestsTotal > 4
                && !candidates.find(candidateQuery.withGuests(2, 0)).isEmpty()) {
            groupTooBig = true;
            notice = "Отели в направлении «" + where.displayName() + "» есть, но номеров на " + guestsTotal
                    + " гостей нет. Компании лучше забронировать несколько номеров — например, поищите на 2–4 человек.";
        }
        if (found.isEmpty() && where != null && !groupTooBig) {
            relaxed = true;
            if (where.city() != null && where.region() != null) {
                found = candidates.find(candidateQuery.withDestination(where.country(), where.region(), null));
                if (!found.isEmpty()) {
                    notice = "В демо-каталоге нет отелей в городе " + where.city()
                            + " — показываем другие места: " + where.region() + ".";
                }
            }
            if (found.isEmpty() && (where.city() != null || where.region() != null)) {
                found = candidates.find(candidateQuery.withDestination(where.country(), null, null));
                if (!found.isEmpty()) {
                    notice = "В демо-каталоге нет отелей: " + where.displayName()
                            + " — показываем другие места: " + where.country() + ".";
                }
            }
            if (found.isEmpty()) {
                found = candidates.find(candidateQuery.withDestination(null, null, null));
                notice = "В демо-каталоге пока нет отелей в направлении «" + where.displayName()
                        + "». Ниже — лучшие варианты по остальным вашим условиям в других странах.";
            }
        } else if (where == null) {
            notice = "Подобрали лучшие варианты по остальным условиям во всех странах.";
        }

        boolean landmarkFar = farLandmark;
        MatchScorer.LandmarkRef landmarkRef = relaxed ? null : landmark
                .map(l -> new MatchScorer.LandmarkRef(l.name(), proximity.maxWalkMinutes(), landmarkFar))
                .orElse(null);
        BigDecimal budgetCeiling = intent.budget() == null ? null
                : intent.budget().totalFor(nights).multiply(BigDecimal.ONE.add(props.budgetTolerance()));

        Map<String, Optional<SeasonInfo>> seasons = new HashMap<>();
        Map<String, Optional<EntryInfo>> entries = new HashMap<>();
        Map<String, List<WildlifeInfo>> wildlife = new HashMap<>();
        // факты пайплайна — одним запросом на всех кандидатов (есть только у отелей проработанного региона)
        Map<UUID, HotelFacts.Profile> verified = facts.profiles(found.stream()
                .filter(c -> "OSM".equals(c.dataSource())).map(HotelCandidate::id).toList());
        List<HotelMatch> ranked = found.stream()
                .map(c -> {
                    Optional<SeasonInfo> season = seasons.computeIfAbsent(c.country() + "|" + c.city(),
                            k -> guide.season(c.country(), c.city(), month));
                    BeachInfo beach = guide.beach(c.beachId()).orElse(null);
                    List<MatchScorer.NearbyBeach> alternatives = intent.activities().isEmpty() ? List.of()
                            : alternativeBeaches(c, beach);
                    Optional<EntryInfo> entry = entries.computeIfAbsent(c.country() + "|" + c.city(),
                            k -> guide.entry(c.country(), c.city()));
                    Integer toBeach = c.distanceToBeachM() != null ? c.distanceToBeachM() : c.beachDistanceM();
                    List<WildlifeInfo> animals = wildlife.computeIfAbsent(c.country() + "|" + c.city(),
                            k -> guide.wildlife(c.country(), c.city()));
                    MatchScorer.Context ctx = new MatchScorer.Context(beach, toBeach, season.orElse(null), month,
                            monthExplicit, alternatives, entry.orElse(null), animals, verified.get(c.id()));
                    return toMatch(intent, c, priceWithMeal(intent, c, nights), landmarkRef, ctx);
                })
                .filter(m -> budgetCeiling == null || m.price().total().compareTo(budgetCeiling) <= 0)
                .sorted(Comparator.comparingInt(HotelMatch::matchPercent).reversed()
                        .thenComparing(m -> m.price().total()))
                .limit(props.maxResults())
                .toList();

        log.info("search: source={} destination={} candidates={} shown={}",
                extraction.source(), intent.destination(), found.size(), ranked.size());

        List<String> clarifications = new ArrayList<>(extraction.clarifications());
        if (ranked.isEmpty() && !groupTooBig) {
            clarifications.add("Под все условия ничего не нашлось. Попробуйте увеличить бюджет или убрать одно из пожеланий.");
        }
        SearchResponse.Understood understood = new SearchResponse.Understood(intent, extraction.source(),
                IntentSummary.of(intent, landmark.map(ResolvedLandmark::name).orElse(null)),
                assumptions, clarifications);
        return new SearchResponse(understood, ranked, found.size(), notice);
    }

    private Optional<ResolvedLandmark> resolveLandmark(SearchIntent intent, List<String> assumptions) {
        if (intent.proximity().isEmpty()) {
            return Optional.empty();
        }
        ProximityConstraint first = intent.proximity().get(0);
        if (intent.destination() == null || intent.destination().city() == null) {
            assumptions.add("Чтобы учесть «" + first.landmark() + "», уточните город");
            return Optional.empty();
        }
        // «базой панд»: пробуем каждое слово фразы — какое-то из них совпадёт с основой названия
        Optional<ResolvedLandmark> resolved = Optional.empty();
        for (String word : first.landmark().split("\\s+")) {
            if (word.length() >= 3 && resolved.isEmpty()) {
                resolved = candidates.resolveLandmark(intent.destination().city(), word);
            }
        }
        if (resolved.isEmpty()) {
            assumptions.add("Не нашли «" + first.landmark() + "» на карте — ищем без этого условия");
        }
        return resolved;
    }

    private List<MatchScorer.NearbyBeach> alternativeBeaches(HotelCandidate c, BeachInfo nearest) {
        return guide.beachesIn(c.city()).stream()
                .filter(b -> nearest == null || !b.id().equals(nearest.id()))
                .map(b -> new MatchScorer.NearbyBeach(b, Distance.meters(c.location().lat(), c.location().lon(), b.lat(), b.lon())))
                .filter(nb -> nb.distanceMeters() <= MatchScorer.ALT_BEACH_RADIUS_M)
                .sorted(Comparator.comparingInt(MatchScorer.NearbyBeach::distanceMeters))
                .toList();
    }

    /**
     * Цена для сравнения с бюджетом. Если человек просит питание, которого нет в тарифе, а отель его добавляет, —
     * доплата сразу входит в цену: иначе отель с завтраками казался бы дешевле, чем обойдётся поездка.
     */
    static PriceQuote priceWithMeal(SearchIntent intent, HotelCandidate c, int nights) {
        PriceQuote base = PriceCalculator.quote(c.rate().pricePerNight(), c.seasonCoefficient(), nights);
        MealPlan wanted = intent.mealPlan();
        if (wanted == null || wanted == c.rate().mealPlan()) {
            return base;
        }
        return c.services().addon(wanted)
                .map(a -> base.withMeal(wanted, Pricing.mealAddonTotal(a.adultPerNight(), a.childPerNight(), a.childFreeUnder(),
                        intent.guests().adults(), intent.guests().childrenAges(), nights)))
                .orElse(base);
    }

    private static HotelMatch toMatch(SearchIntent intent, HotelCandidate c, PriceQuote price,
                                      MatchScorer.LandmarkRef landmark, MatchScorer.Context ctx) {
        MatchScorer.Scored scored = MatchScorer.score(intent, c, price, landmark, ctx);
        // у пляжной линии отеля расстояние точнее, чем до «центральной точки» длинного пляжа
        Integer toBeach = ctx.beachDistanceM();
        BeachView beach = ctx.beach() == null || toBeach == null ? null
                : new BeachView(ctx.beach(), toBeach, WalkingTime.minutes(toBeach));
        return new HotelMatch(c.id(), c.name(), c.country(), c.region(), c.city(), c.district(), c.stars(), c.rating(),
                c.reviewsCount(), c.description(), c.location(), scored.matchPercent(), price, c.rate().rateId(),
                c.rate().roomName(), c.rate().mealPlan(), c.rate().refundable(), scored.reasons(), c.nearby(),
                beach, ctx.season(), c.accessibility(), c.policies(), ctx.entry(), c.surroundings(), ctx.wildlife(), c.services(), c.dataSource(), ctx.verified());
    }
}
