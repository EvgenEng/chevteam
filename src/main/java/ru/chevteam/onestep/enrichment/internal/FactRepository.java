package ru.chevteam.onestep.enrichment.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.chevteam.onestep.enrichment.FactSource;
import ru.chevteam.onestep.enrichment.HotelFact;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.tools.Site;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Факты, заявления отельера, пользовательские характеристики и журнал запусков. */
@Repository
class FactRepository {

    private final JdbcClient jdbc;

    FactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Отели региона, для которых считаем характеристики: только видимые в выдаче. */
    List<Site> sites(EnrichmentProperties p) {
        return jdbc.sql("""
                        SELECT id, name, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon
                          FROM hotel
                         WHERE listed
                           AND ST_Y(location::geometry) BETWEEN :south AND :north
                           AND ST_X(location::geometry) BETWEEN :west AND :east
                         ORDER BY name
                        """)
                .param("south", p.south()).param("north", p.north()).param("west", p.west()).param("east", p.east())
                .query((rs, n) -> new Site(rs.getObject("id", UUID.class), rs.getString("name"),
                        new Point(rs.getDouble("lat"), rs.getDouble("lon"))))
                .list();
    }

    /** Заменяет все факты источника для отеля одним махом: старые выводы не должны «висеть». */
    void replace(UUID hotelId, FactSource source, List<HotelFact> facts) {
        jdbc.sql("DELETE FROM hotel_fact WHERE hotel_id = :id AND source = :source")
                .param("id", hotelId).param("source", source.name()).update();
        for (HotelFact f : facts) {
            jdbc.sql("""
                            INSERT INTO hotel_fact (hotel_id, code, source, title, value, level, number, confidence, method,
                                                    evidence, computed_at)
                            VALUES (:hotel, :code, :source, :title, :value, :level, :number, :confidence, :method, :evidence, :at)
                            """)
                    .param("hotel", hotelId).param("code", f.code()).param("source", source.name())
                    .param("title", cut(f.title(), 120)).param("value", cut(f.value(), 300)).param("level", f.level())
                    .param("number", f.number()).param("confidence", BigDecimal.valueOf(f.confidence()).setScale(2, java.math.RoundingMode.HALF_UP))
                    .param("method", cut(f.method(), 300)).param("evidence", cut(f.evidence(), 600))
                    .param("at", Timestamp.from(f.computedAt()))
                    .update();
        }
    }

    /**
     * Проекция фактов в поля карточки, по которым уже работают поиск и скоринг:
     * расстояние до моря и линия пляжа, шум дорог. Для этих отелей источник правды — пайплайн.
     */
    void project(UUID hotelId, Double seaMeters, String noiseLevel, String noiseEvidence) {
        Integer meters = seaMeters == null ? null : (int) Math.round(seaMeters);
        Integer line = null;
        if (meters != null && meters <= 800) {
            line = meters <= 150 ? 1 : meters <= 400 ? 2 : 3;
        }
        jdbc.sql("""
                        UPDATE hotel
                           SET distance_to_beach_m = :meters, beach_line = :line,
                               road_noise = COALESCE(:noise, road_noise), road_note = COALESCE(:note, road_note)
                         WHERE id = :id AND data_source = 'OSM'
                        """)
                .param("meters", meters).param("line", line).param("noise", noiseLevel).param("note", noiseEvidence)
                .param("id", hotelId)
                .update();
    }

    List<HotelFact> facts(Collection<UUID> hotelIds) {
        if (hotelIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT hotel_id, code, source, title, value, level, number, confidence, method, evidence, computed_at
                          FROM hotel_fact WHERE hotel_id IN (:ids) ORDER BY hotel_id, code, source
                        """)
                .param("ids", hotelIds)
                .query((rs, n) -> new HotelFact(rs.getObject("hotel_id", UUID.class), rs.getString("code"), rs.getString("title"),
                        rs.getString("value"), rs.getString("level"), (Double) rs.getObject("number"),
                        rs.getBigDecimal("confidence").doubleValue(), FactSource.valueOf(rs.getString("source")),
                        rs.getString("method"), rs.getString("evidence"), rs.getTimestamp("computed_at").toInstant()))
                .list();
    }

    List<Claim> claims(Collection<UUID> hotelIds) {
        if (hotelIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT hotel_id, code, level, text, author, declared_at FROM hotel_claim WHERE hotel_id IN (:ids)")
                .param("ids", hotelIds)
                .query((rs, n) -> new Claim(rs.getObject("hotel_id", UUID.class), rs.getString("code"), rs.getString("level"),
                        rs.getString("text"), rs.getString("author"), rs.getTimestamp("declared_at").toInstant()))
                .list();
    }

    void saveClaim(Claim c) {
        jdbc.sql("""
                        INSERT INTO hotel_claim (hotel_id, code, level, text, author, declared_at)
                        VALUES (:hotel, :code, :level, :text, :author, :at)
                        ON CONFLICT (hotel_id, code) DO UPDATE
                           SET level = EXCLUDED.level, text = EXCLUDED.text, author = EXCLUDED.author, declared_at = EXCLUDED.declared_at
                        """)
                .param("hotel", c.hotelId()).param("code", c.code()).param("level", c.level())
                .param("text", cut(c.text(), 300)).param("author", cut(c.author(), 120)).param("at", Timestamp.from(c.declaredAt()))
                .update();
    }

    boolean hotelExists(UUID id) {
        return jdbc.sql("SELECT COUNT(*) FROM hotel WHERE id = :id").param("id", id).query(Integer.class).single() > 0;
    }

    Optional<String> hotelName(UUID id) {
        return jdbc.sql("SELECT name FROM hotel WHERE id = :id").param("id", id).query(String.class).optional();
    }

    List<Custom> customs() {
        return jdbc.sql("SELECT code, title, source_text, recipe, created_at, status FROM custom_characteristic ORDER BY created_at")
                .query((rs, n) -> new Custom(rs.getString("code"), rs.getString("title"), rs.getString("source_text"),
                        rs.getString("recipe"), rs.getTimestamp("created_at").toInstant(), rs.getString("status")))
                .list();
    }

    void setCustomStatus(String code, String status) {
        jdbc.sql("UPDATE custom_characteristic SET status = :status WHERE code = :code")
                .param("status", status).param("code", code).update();
    }

    // ------------------------------------------------------------------ заявки агента данных

    void saveRequest(SourceRequest r) {
        jdbc.sql("""
                        INSERT INTO source_request (id, characteristic_code, filter, query, reason, status, created_at)
                        VALUES (:id, :code, :filter, :query, :reason, :status, :at)
                        """)
                .param("id", r.id()).param("code", r.characteristicCode()).param("filter", cut(r.filter(), 300))
                .param("query", r.query()).param("reason", cut(r.reason(), 600)).param("status", r.status().name())
                .param("at", Timestamp.from(r.createdAt()))
                .update();
    }

    /** Решение по заявке; {@code data} — скачанный ответ Overpass (хранится в базе, переживает перезапуск). */
    void decide(UUID id, SourceRequest.Status status, String by, Instant at, Integer elements, String message, String data) {
        jdbc.sql("""
                        UPDATE source_request
                           SET status = :status, decided_by = :by, decided_at = :at, elements = :elements, message = :message,
                               data = COALESCE(:data, data)
                         WHERE id = :id
                        """)
                .param("status", status.name()).param("by", cut(by, 120)).param("at", Timestamp.from(at))
                .param("elements", elements).param("message", cut(message, 500)).param("data", data).param("id", id)
                .update();
    }

    Optional<SourceRequest> request(UUID id) {
        return jdbc.sql(REQUEST_SELECT + " WHERE id = :id").param("id", id).query(FactRepository::request).optional();
    }

    List<SourceRequest> requests(int limit) {
        return jdbc.sql(REQUEST_SELECT + " ORDER BY created_at DESC LIMIT :limit").param("limit", limit)
                .query(FactRepository::request).list();
    }

    /** Открытая заявка на те же объекты — чтобы две характеристики про парковку не плодили две заявки. */
    Optional<SourceRequest> openRequest(String filter) {
        return jdbc.sql(REQUEST_SELECT + " WHERE filter = :filter AND status IN ('PENDING', 'FAILED') ORDER BY created_at LIMIT 1")
                .param("filter", filter).query(FactRepository::request).optional();
    }

    /** Докачанные источники: фильтр (что покрывают) и ответ Overpass. */
    List<String[]> fetchedSources() {
        return jdbc.sql("SELECT filter, data FROM source_request WHERE status = 'FETCHED' AND data IS NOT NULL ORDER BY decided_at")
                .query((rs, n) -> new String[] {rs.getString("filter"), rs.getString("data")})
                .list();
    }

    private static final String REQUEST_SELECT = """
            SELECT id, characteristic_code, filter, query, reason, status, created_at, decided_at, decided_by, elements, message
              FROM source_request""";

    private static SourceRequest request(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Timestamp decided = rs.getTimestamp("decided_at");
        return new SourceRequest(rs.getObject("id", UUID.class), rs.getString("characteristic_code"), rs.getString("filter"),
                rs.getString("query"), rs.getString("reason"), SourceRequest.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), decided == null ? null : decided.toInstant(), rs.getString("decided_by"),
                (Integer) rs.getObject("elements"), rs.getString("message"));
    }

    void saveCustom(Custom c) {
        jdbc.sql("""
                        INSERT INTO custom_characteristic (code, title, source_text, recipe, created_at, status)
                        VALUES (:code, :title, :text, :recipe, :at, :status)
                        ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title, source_text = EXCLUDED.source_text,
                                                         recipe = EXCLUDED.recipe, created_at = EXCLUDED.created_at,
                                                         status = EXCLUDED.status
                        """)
                .param("code", c.code()).param("title", cut(c.title(), 120)).param("text", cut(c.sourceText(), 300))
                .param("recipe", c.recipe()).param("at", Timestamp.from(c.createdAt())).param("status", c.status())
                .update();
    }

    void deleteFacts(String code) {
        jdbc.sql("DELETE FROM hotel_fact WHERE code = :code").param("code", code).update();
    }

    List<String> fetchedFilters() {
        return jdbc.sql("SELECT filter FROM source_request WHERE status = 'FETCHED' ORDER BY decided_at").query(String.class).list();
    }

    boolean deleteCustom(String code) {
        jdbc.sql("DELETE FROM hotel_fact WHERE code = :code").param("code", code).update();
        return jdbc.sql("DELETE FROM custom_characteristic WHERE code = :code").param("code", code).update() > 0;
    }

    void logRun(PipelineRun run) {
        jdbc.sql("""
                        INSERT INTO enrichment_run (id, started_at, finished_at, source, hotels, facts, disputes, message)
                        VALUES (:id, :started, :finished, :source, :hotels, :facts, :disputes, :message)
                        """)
                .param("id", run.id()).param("started", Timestamp.from(run.startedAt()))
                .param("finished", Timestamp.from(run.finishedAt())).param("source", cut(run.source(), 200))
                .param("hotels", run.hotels()).param("facts", run.facts()).param("disputes", run.disputes())
                .param("message", cut(run.message(), 500))
                .update();
    }

    List<PipelineRun> runs(int limit) {
        return jdbc.sql("SELECT * FROM enrichment_run ORDER BY started_at DESC LIMIT :limit")
                .param("limit", limit)
                .query((rs, n) -> new PipelineRun(rs.getObject("id", UUID.class), rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("finished_at").toInstant(), rs.getString("source"), rs.getInt("hotels"),
                        rs.getInt("facts"), rs.getInt("disputes"), rs.getString("message")))
                .list();
    }

    /** Сколько фактов по каждому коду — для экрана администратора. */
    Map<String, Integer> factCounts() {
        Map<String, Integer> result = new java.util.TreeMap<>();
        jdbc.sql("SELECT code, COUNT(*) AS n FROM hotel_fact GROUP BY code")
                .query(rs -> {
                    result.put(rs.getString("code"), rs.getInt("n"));
                });
        return result;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    record Claim(UUID hotelId, String code, String level, String text, String author, Instant declaredAt) {
    }

    /** @param status {@code ACTIVE} — считается; {@code NEEDS_DATA} — ждёт, пока админ одобрит докачку объектов */
    record Custom(String code, String title, String sourceText, String recipe, Instant createdAt, String status) {

        static final String ACTIVE = "ACTIVE";
        static final String NEEDS_DATA = "NEEDS_DATA";
    }

    static List<UUID> ids(Collection<UUID> ids) {
        return new ArrayList<>(ids);
    }
}
