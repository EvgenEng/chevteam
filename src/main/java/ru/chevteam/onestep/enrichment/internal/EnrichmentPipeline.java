package ru.chevteam.onestep.enrichment.internal;

import ru.chevteam.onestep.enrichment.OsmSourceFetcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.chevteam.onestep.enrichment.CardScore;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.FactSource;
import ru.chevteam.onestep.enrichment.HotelFact;
import ru.chevteam.onestep.enrichment.HotelFactsUpdated;
import ru.chevteam.onestep.enrichment.internal.geo.DemGrid;
import ru.chevteam.onestep.enrichment.internal.osm.DataCoverage;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.osm.OverpassQuery;
import ru.chevteam.onestep.enrichment.internal.tools.BuiltIns;
import ru.chevteam.onestep.enrichment.internal.tools.Characteristic;
import ru.chevteam.onestep.enrichment.internal.tools.Computed;
import ru.chevteam.onestep.enrichment.internal.tools.Recipe;
import ru.chevteam.onestep.enrichment.internal.tools.Site;
import ru.chevteam.onestep.enrichment.internal.tools.TerrainCharacteristics;
import ru.chevteam.onestep.enrichment.internal.tools.TextRecipes;
import ru.chevteam.onestep.shared.error.BusinessRuleException;
import ru.chevteam.onestep.shared.error.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Пайплайн: снимок OSM → для каждого отеля региона все характеристики (встроенные и добавленные текстом)
 * → факты с доказательствами → проекция в карточку для поиска → сверка с заявлениями отельера.
 * <p>
 * Идемпотентен: повторный запуск заменяет факты, а не дублирует. Снимок читается один раз и держится в памяти.
 */
@Service
public class EnrichmentPipeline {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentPipeline.class);

    private final OsmSnapshotLoader loader;
    private final FactRepository repo;
    private final EnrichmentProperties props;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicReference<OsmSnapshotLoader.Loaded> snapshot = new AtomicReference<>();
    private final AtomicReference<Optional<DemGrid>> dem = new AtomicReference<>();
    private final AtomicReference<Optional<DataCoverage>> baseCoverage = new AtomicReference<>();
    private final OsmSourceFetcher fetcher;

    EnrichmentPipeline(OsmSnapshotLoader loader, FactRepository repo, EnrichmentProperties props, ObjectMapper mapper,
                       ApplicationEventPublisher events, TransactionTemplate tx, Clock clock, OsmSourceFetcher fetcher) {
        this.fetcher = fetcher;
        this.loader = loader;
        this.repo = repo;
        this.props = props;
        this.mapper = mapper;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        if (props.runOnStartup()) {
            Thread.ofVirtual().name("enrichment-startup").start(() -> {
                try {
                    run("старт приложения");
                } catch (RuntimeException e) {
                    log.warn("Enrichment pipeline failed on startup: {}", e.toString());
                }
            });
        }
    }

    public String region() {
        return props.region();
    }

    /** Полный пересчёт по всем отелям региона. */
    public PipelineRun run(String reason) {
        if (!running.tryLock()) {
            throw new BusinessRuleException("Пайплайн уже выполняется — дождитесь окончания");
        }
        try {
            Instant started = clock.instant();
            OsmSnapshotLoader.Loaded loaded = snapshot();
            List<Characteristic> characteristics = characteristics();
            List<Site> sites = repo.sites(props);
            int facts = 0;
            for (Site site : sites) {
                facts += computeAndStore(site, loaded.snapshot(), characteristics, started);
            }
            int disputes = (int) checks(sites.stream().map(Site::hotelId).toList()).values().stream()
                    .flatMap(List::stream).filter(c -> c.status() == ClaimStatus.DISPUTED).count();
            PipelineRun run = new PipelineRun(UUID.randomUUID(), started, clock.instant(), loaded.description(), sites.size(), facts,
                    disputes, reason + ": " + characteristics.size() + " характеристик" + (dem().isPresent() ? ", с картой высот SRTM" : "")
                    + ", снимок OSM от "
                    + (loaded.snapshot().takenAt() != null ? loaded.snapshot().takenAt() : "неизвестной даты"));
            repo.logRun(run);
            log.info("Enrichment: {} hotels, {} facts, {} disputes in {} ms", run.hotels(), run.facts(), run.disputes(), run.millis());
            events.publishEvent(new HotelFactsUpdated(run.hotels(), reason));
            return run;
        } finally {
            running.unlock();
        }
    }

    private int computeAndStore(Site site, OsmSnapshot osm, List<Characteristic> characteristics, Instant at) {
        List<HotelFact> facts = new ArrayList<>();
        for (Characteristic c : characteristics) {
            Optional<Computed> computed;
            try {
                computed = c.compute(site, osm);
            } catch (RuntimeException e) {
                // одна сломанная характеристика не должна валить весь пересчёт
                log.warn("Characteristic {} failed for {}: {}", c.code(), site.name(), e.toString());
                continue;
            }
            computed.ifPresent(r -> facts.add(new HotelFact(site.hotelId(), c.code(), c.title(), r.value(), r.level(), r.number(),
                    r.confidence(), FactSource.OSM, r.method(), r.evidence(), at)));
        }
        Double sea = facts.stream().filter(f -> BuiltIns.SEA_DISTANCE.equals(f.code())).map(HotelFact::number).findFirst().orElse(null);
        HotelFact noise = facts.stream().filter(f -> BuiltIns.ROAD_NOISE.equals(f.code())).findFirst().orElse(null);
        tx.executeWithoutResult(s -> {
            repo.replace(site.hotelId(), FactSource.OSM, facts);
            repo.project(site.hotelId(), sea, noise == null ? null : noise.level(), noise == null ? null : noise.evidence());
        });
        return facts.size();
    }

    private Optional<DemGrid> dem() {
        Optional<DemGrid> d = dem.get();
        if (d == null) {
            d = Optional.ofNullable(loader.loadDem());
            dem.set(d);
            log.info("Enrichment: elevation model {}", d.isPresent() ? "loaded" : "not found — terrain characteristics skipped");
        }
        return d;
    }

    /** Снимок из jar плюс источники, которые агент данных докачал с одобрения админа (хранятся в базе). */
    private OsmSnapshotLoader.Loaded snapshot() {
        OsmSnapshotLoader.Loaded s = snapshot.get();
        if (s == null) {
            s = loader.load();
            List<String[]> extra = repo.fetchedSources();
            if (!extra.isEmpty()) {
                List<OsmElement> all = new ArrayList<>(s.snapshot().elements());
                List<String> filters = new ArrayList<>();
                for (String[] source : extra) {
                    try {
                        all.addAll(loader.parse(source[1]).elements());
                        filters.add(source[0]);
                    } catch (RuntimeException e) {
                        log.warn("Skipping broken fetched source {}: {}", source[0], e.toString());
                    }
                }
                s = new OsmSnapshotLoader.Loaded(new OsmSnapshot(all, s.snapshot().takenAt()),
                        s.description() + " + докачано агентом данных: " + String.join(", ", filters));
            }
            snapshot.set(s);
        }
        return s;
    }

    /** Что выгружено: манифест снимка плюс одобренные докачки. Пусто — манифеста нет, проверку покрытия не делаем. */
    private Optional<DataCoverage> coverage() {
        Optional<DataCoverage> base = baseCoverage.get();
        if (base == null) {
            base = Optional.ofNullable(loader.loadCoverage());
            baseCoverage.set(base);
        }
        return base.map(c -> {
            DataCoverage result = c;
            for (String filter : repo.fetchedFilters()) {
                result = result.with(filter);
            }
            return result;
        });
    }

    List<Characteristic> characteristics() {
        List<Characteristic> all = new ArrayList<>(BuiltIns.all());
        dem().ifPresent(d -> all.addAll(TerrainCharacteristics.all(d)));
        for (FactRepository.Custom c : repo.customs()) {
            if (!FactRepository.Custom.ACTIVE.equals(c.status())) {
                continue; // ждёт данных: без них «рядом нет» было бы неправдой
            }
            try {
                all.add(mapper.readValue(c.recipe(), Recipe.class).characteristic());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("Skipping broken custom characteristic {}: {}", c.code(), e.toString());
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ «добавить характеристику текстом»

    /** Текст → рецепт из готовых инструментов → сохраняем → пересчитываем для всех отелей региона. */
    public AddedCharacteristic addFromText(String text) {
        Recipe recipe = TextRecipes.parse(text).orElseThrow(() -> new BusinessRuleException(
                "Не поняли, что искать на карте. Назовите объект: «аптека в 5 минутах», «нет ночных клубов рядом», «много кафе рядом»"));
        String json;
        try {
            json = mapper.writeValueAsString(recipe);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Optional<String> missing = coverage().flatMap(c -> c.missing(recipe.filter()));
        if (missing.isPresent()) {
            // агент данных: объектов нет в снимке — не считаем, а предлагаем админу докачать
            repo.saveCustom(new FactRepository.Custom(recipe.code(), recipe.title(), text.strip(), json, clock.instant(),
                    FactRepository.Custom.NEEDS_DATA));
            repo.deleteFacts(recipe.code());
            SourceRequest request = repo.openRequest(missing.get()).orElseGet(() -> propose(recipe, missing.get()));
            events.publishEvent(new HotelFactsUpdated(0, "характеристика ждёт данных"));
            return new AddedCharacteristic(recipe, null, 0, request);
        }
        repo.saveCustom(new FactRepository.Custom(recipe.code(), recipe.title(), text.strip(), json, clock.instant(),
                FactRepository.Custom.ACTIVE));
        PipelineRun run = run("новая характеристика «" + recipe.title() + "»");
        return new AddedCharacteristic(recipe, run, yesCount(recipe.code()), null);
    }

    private int yesCount(String code) {
        return (int) repo.facts(repo.sites(props).stream().map(Site::hotelId).toList()).stream()
                .filter(f -> code.equals(f.code()) && "YES".equals(f.level())).count();
    }

    // ------------------------------------------------------------------ агент данных

    private SourceRequest propose(Recipe recipe, String missing) {
        String reason = "Чтобы посчитать «" + recipe.title() + "», нужны объекты OpenStreetMap «" + missing + "» (" + recipe.noun()
                + "), а в снимке региона их нет — мы их не выгружали. Если посчитать сейчас, у всех отелей выйдет «рядом нет», "
                + "и это будет неправдой. Предлагаю скачать из открытого Overpass API только эти объекты в границах региона «"
                + props.region() + "» (данные OpenStreetMap, лицензия ODbL).";
        SourceRequest request = new SourceRequest(UUID.randomUUID(), recipe.code(), missing,
                OverpassQuery.build(missing, props.south(), props.west(), props.north(), props.east()), reason,
                SourceRequest.Status.PENDING, clock.instant(), null, null, null, null);
        repo.saveRequest(request);
        log.info("Data agent: proposed to fetch {} for {}", missing, recipe.code());
        return request;
    }

    /** Админ одобрил: скачиваем, кладём в базу, включаем характеристики, которым хватает данных, и пересчитываем. */
    public Decision approve(UUID id, String by) {
        SourceRequest request = open(id);
        String who = by == null || by.isBlank() ? "администратор" : by.strip();
        OsmSnapshot fetched;
        OsmSourceFetcher.Fetched response;
        try {
            response = fetcher.fetch(request.query());
            fetched = loader.parse(response.json());
        } catch (RuntimeException e) {
            repo.decide(id, SourceRequest.Status.FAILED, who, clock.instant(), null, e.getMessage(), null);
            log.warn("Data agent: fetch of {} failed: {}", request.filter(), e.toString());
            return new Decision(repo.request(id).orElseThrow(), null, List.of());
        }
        OsmFilter filter = OsmFilter.parse(request.filter());
        int found = (int) fetched.elements().stream().filter(filter).count();
        repo.decide(id, SourceRequest.Status.FETCHED, who, clock.instant(), found,
                found == 0 ? "скачано с " + response.server() + ": таких объектов в регионе нет — теперь это проверенный факт"
                        : "скачано с " + response.server() + ": объектов — " + found, response.json());
        snapshot.set(null);
        DataCoverage now = coverage().orElse(null);
        List<String> activated = new ArrayList<>();
        for (FactRepository.Custom c : repo.customs()) {
            if (FactRepository.Custom.NEEDS_DATA.equals(c.status()) && (now == null || now.missing(recipeOf(c).filter()).isEmpty())) {
                repo.setCustomStatus(c.code(), FactRepository.Custom.ACTIVE);
                activated.add(c.title());
            }
        }
        PipelineRun run = run("агент данных: докачаны объекты «" + request.filter() + "»");
        return new Decision(repo.request(id).orElseThrow(), run, activated);
    }

    /** Админ отклонил: ничего не скачиваем, характеристики, которые ждали этих данных, убираем. */
    public Decision reject(UUID id, String by) {
        SourceRequest request = open(id);
        List<String> removed = new ArrayList<>();
        Optional<DataCoverage> now = coverage();
        for (FactRepository.Custom c : repo.customs()) {
            if (FactRepository.Custom.NEEDS_DATA.equals(c.status())
                    && now.flatMap(cov -> cov.missing(recipeOf(c).filter())).map(request.filter()::equals).orElse(false)) {
                repo.deleteCustom(c.code());
                removed.add(c.title());
            }
        }
        repo.decide(id, SourceRequest.Status.REJECTED, by == null || by.isBlank() ? "администратор" : by.strip(), clock.instant(),
                null, removed.isEmpty() ? "отклонено" : "отклонено — без данных не считаем: " + String.join(", ", removed), null);
        return new Decision(repo.request(id).orElseThrow(), null, removed);
    }

    public List<SourceRequest> sourceRequests() {
        return repo.requests(20);
    }

    private SourceRequest open(UUID id) {
        SourceRequest request = repo.request(id).orElseThrow(() -> new NotFoundException("Заявка " + id + " не найдена"));
        if (request.status() != SourceRequest.Status.PENDING && request.status() != SourceRequest.Status.FAILED) {
            throw new BusinessRuleException("По заявке уже есть решение: " + request.status());
        }
        return request;
    }

    private Recipe recipeOf(FactRepository.Custom c) {
        try {
            return mapper.readValue(c.recipe(), Recipe.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Сломанный рецепт " + c.code(), e);
        }
    }

    public List<Map<String, Object>> customs() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> counts = repo.factCounts();
        for (FactRepository.Custom c : repo.customs()) {
            result.add(Map.of("code", c.code(), "title", c.title(), "sourceText", c.sourceText(), "recipe", c.recipe(),
                    "hotels", counts.getOrDefault(c.code(), 0), "status", c.status()));
        }
        return result;
    }

    public void deleteCustom(String code) {
        if (!repo.deleteCustom(code)) {
            throw new NotFoundException("Характеристика " + code + " не найдена");
        }
        events.publishEvent(new HotelFactsUpdated(0, "удалена характеристика " + code));
    }

    /** Отели региона — для выбора в экстранете. */
    public List<Map<String, Object>> hotels() {
        return repo.sites(props).stream()
                .<Map<String, Object>>map(site -> Map.of("id", site.hotelId(), "name", site.name()))
                .toList();
    }

    public List<PipelineRun> runs() {
        return repo.runs(10);
    }

    public Map<String, Integer> factCounts() {
        return repo.factCounts();
    }

    // ------------------------------------------------------------------ экстранет отельера

    /** Заявление отельера: сохраняем и сразу сверяем с картой. */
    public ClaimCheck declare(UUID hotelId, String code, String level, String author) {
        if (!repo.hotelExists(hotelId)) {
            throw new NotFoundException("Отель " + hotelId + " не найден");
        }
        Map<String, String> options = ClaimVerifier.CLAIMS.get(code);
        if (options == null || !options.containsKey(level)) {
            throw new BusinessRuleException("Такого заявления нет: " + code + " = " + level);
        }
        repo.saveClaim(new FactRepository.Claim(hotelId, code, level, options.get(level),
                author == null || author.isBlank() ? "отельер" : author.strip(), clock.instant()));
        events.publishEvent(new HotelFactsUpdated(1, "заявление отельера"));
        return checks(List.of(hotelId)).getOrDefault(hotelId, List.of()).stream()
                .filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    /** Экстранет: уровень карточки, что сделать до следующего и список задач по сверке с картой. */
    public HotelierBoard board(UUID hotelId) {
        if (!repo.hotelExists(hotelId)) {
            throw new NotFoundException("Отель " + hotelId + " не найден");
        }
        List<ClaimCheck> checks = checks(List.of(hotelId)).getOrDefault(hotelId, List.of());
        CardScore score = CardScoring.score(checks);
        return new HotelierBoard(score, score.level().title(), CardScoring.next(score),
                CardScoring.tasks(repo.facts(List.of(hotelId)), checks));
    }

    /** Варианты заявлений для формы экстранета. */
    public Map<String, Map<String, String>> claimOptions() {
        return ClaimVerifier.CLAIMS;
    }

    Map<UUID, List<ClaimCheck>> checks(List<UUID> hotelIds) {
        Map<UUID, List<HotelFact>> facts = new java.util.HashMap<>();
        for (HotelFact f : repo.facts(hotelIds)) {
            facts.computeIfAbsent(f.hotelId(), k -> new ArrayList<>()).add(f);
        }
        Map<UUID, List<ClaimCheck>> result = new java.util.HashMap<>();
        for (FactRepository.Claim c : repo.claims(hotelIds)) {
            Optional<HotelFact> fact = facts.getOrDefault(c.hotelId(), List.of()).stream()
                    .filter(f -> f.code().equals(c.code()) && f.source() == FactSource.OSM).findFirst();
            result.computeIfAbsent(c.hotelId(), k -> new ArrayList<>()).add(ClaimVerifier.check(c, fact));
        }
        return result;
    }

    /**
     * Результат добавления характеристики: рецепт, пересчёт и у скольких отелей ответ «да».
     * Если данных в снимке нет — {@code run} пуст, а {@code dataRequest} — заявка агента данных на одобрение.
     */
    public record AddedCharacteristic(Recipe recipe, PipelineRun run, int hotelsWithYes, SourceRequest dataRequest) {

        public boolean needsData() {
            return dataRequest != null;
        }
    }

    /** Экстранет отельера: карточка, следующий шаг и задачи. */
    public record HotelierBoard(CardScore score, String levelTitle, String next, List<HotelierTask> tasks) {
    }

    /** Решение по заявке агента: заявка, пересчёт (если был) и какие характеристики включены или убраны. */
    public record Decision(SourceRequest request, PipelineRun run, List<String> characteristics) {
    }
}
