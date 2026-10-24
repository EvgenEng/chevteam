package ru.chevteam.onestep.search.internal;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.search.HotelMatch;
import ru.chevteam.onestep.search.HotelSearch;
import ru.chevteam.onestep.search.PriceQuote;
import ru.chevteam.onestep.search.SearchResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private final SearchProperties props;
    private final Clock clock;

    DefaultHotelSearch(IntentExtractor intentExtractor, CandidateRepository candidates,
                       SearchProperties props, Clock clock) {
        this.intentExtractor = intentExtractor;
        this.candidates = candidates;
        this.props = props;
        this.clock = clock;
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
        int month = intent.stay().month() != null
                ? intent.stay().month().getMonthValue()
                : LocalDate.now(clock).getMonthValue();

        CandidateQuery candidateQuery = new CandidateQuery(
                intent.destination() != null ? intent.destination().country() : null,
                intent.destination() != null ? intent.destination().city() : null,
                intent.minStars(),
                intent.guests().adults(),
                intent.guests().children(),
                intent.mealPlan(),
                month,
                landmark.map(ResolvedLandmark::id).orElse(null),
                proximity != null ? WalkingTime.radiusMeters(proximity.maxWalkMinutes() * PROXIMITY_SLACK) : null,
                CANDIDATE_LIMIT);

        List<HotelCandidate> found = candidates.find(candidateQuery);
        MatchScorer.LandmarkRef landmarkRef = landmark
                .map(l -> new MatchScorer.LandmarkRef(l.name(), proximity.maxWalkMinutes()))
                .orElse(null);
        BigDecimal budgetCeiling = intent.budget() == null ? null
                : intent.budget().totalFor(nights).multiply(BigDecimal.ONE.add(props.budgetTolerance()));

        List<HotelMatch> ranked = found.stream()
                .map(c -> toMatch(intent, c, PriceCalculator.quote(c.rate().pricePerNight(), c.seasonCoefficient(), nights),
                        landmarkRef))
                .filter(m -> budgetCeiling == null || m.price().total().compareTo(budgetCeiling) <= 0)
                .sorted(Comparator.comparingInt(HotelMatch::matchPercent).reversed()
                        .thenComparing(m -> m.price().total()))
                .limit(props.maxResults())
                .toList();

        log.info("search: source={} destination={} candidates={} shown={}",
                extraction.source(), intent.destination(), found.size(), ranked.size());

        List<String> clarifications = new ArrayList<>(extraction.clarifications());
        if (ranked.isEmpty()) {
            clarifications.add("Под все условия ничего не нашлось. Попробуйте увеличить бюджет или убрать одно из пожеланий.");
        }
        SearchResponse.Understood understood = new SearchResponse.Understood(intent, extraction.source(),
                IntentSummary.of(intent, landmark.map(ResolvedLandmark::name).orElse(null)),
                assumptions, clarifications);
        return new SearchResponse(understood, ranked, found.size());
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
        Optional<ResolvedLandmark> resolved = candidates.resolveLandmark(intent.destination().city(), first.landmark());
        if (resolved.isEmpty()) {
            assumptions.add("Не нашли «" + first.landmark() + "» на карте — ищем без этого условия");
        }
        return resolved;
    }

    private static HotelMatch toMatch(SearchIntent intent, HotelCandidate c, PriceQuote price,
                                      MatchScorer.LandmarkRef landmark) {
        MatchScorer.Scored scored = MatchScorer.score(intent, c, price, landmark);
        return new HotelMatch(c.id(), c.name(), c.city(), c.district(), c.stars(), c.rating(), c.reviewsCount(),
                c.description(), c.location(), scored.matchPercent(), price, c.rate().roomName(),
                c.rate().mealPlan(), c.rate().refundable(), scored.reasons(), c.nearby());
    }
}
