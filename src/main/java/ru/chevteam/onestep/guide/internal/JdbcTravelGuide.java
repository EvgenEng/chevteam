package ru.chevteam.onestep.guide.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ru.chevteam.onestep.guide.BeachEntry;
import ru.chevteam.onestep.guide.BeachInfo;
import ru.chevteam.onestep.guide.BeachSurface;
import ru.chevteam.onestep.guide.Chance;
import ru.chevteam.onestep.guide.Comfort;
import ru.chevteam.onestep.guide.CrowdLevel;
import ru.chevteam.onestep.guide.EntryInfo;
import ru.chevteam.onestep.guide.EntryRegime;
import ru.chevteam.onestep.guide.HazardKind;
import ru.chevteam.onestep.guide.RainLevel;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.Severity;
import ru.chevteam.onestep.guide.TideLevel;
import ru.chevteam.onestep.guide.TravelGuide;
import ru.chevteam.onestep.guide.ViewingMode;
import ru.chevteam.onestep.guide.WaveLevel;
import ru.chevteam.onestep.guide.WildlifeInfo;
import ru.chevteam.onestep.intent.Wildlife;
import ru.chevteam.onestep.intent.BeachActivity;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Справочник читается целиком (десятки пляжей, сотни строк климата) и держится в памяти.
 * Снимок обновляется раз в {@link #TTL}: данные меняются миграциями, а не пользователями.
 * Загрузка ленивая — к первому запросу Liquibase гарантированно отработал.
 */
@Service
class JdbcTravelGuide implements TravelGuide {

    private static final Logger log = LoggerFactory.getLogger(JdbcTravelGuide.class);
    static final Duration TTL = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    JdbcTravelGuide(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<BeachInfo> beach(UUID beachId) {
        return beachId == null ? Optional.empty() : Optional.ofNullable(data().beaches().get(beachId));
    }

    @Override
    public Optional<EntryInfo> entry(String country, String city) {
        if (country == null) {
            return Optional.empty();
        }
        Map<String, EntryInfo> rules = data().entry();
        EntryInfo specific = city == null ? null : rules.get(country + "|" + city);
        return Optional.ofNullable(specific != null ? specific : rules.get(country + "|"));
    }

    @Override
    public List<WildlifeInfo> wildlife(String country, String city) {
        return country == null || city == null ? List.of() : data().wildlife().getOrDefault(country + "|" + city, List.of());
    }

    @Override
    public List<BeachInfo> beachesIn(String city) {
        return city == null ? List.of() : data().beachesByCity().getOrDefault(city, List.of());
    }

    @Override
    public Optional<SeasonInfo> season(String country, String city, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be in [1, 12]: " + month);
        }
        Snapshot data = data();
        String region = data.regions().get(country + "|" + city);
        if (region == null) {
            return Optional.empty();
        }
        ClimateRow climate = data.climate().get(region + "|" + month);
        if (climate == null) {
            return Optional.empty();
        }
        List<SeasonInfo.SeasonalHazard> hazards = data.hazards().getOrDefault(region, List.of()).stream()
                .filter(h -> h.months().contains(month))
                .map(h -> new SeasonInfo.SeasonalHazard(h.kind(), h.severity(), h.note()))
                .toList();
        return Optional.of(new SeasonInfo(month, climate.airC(), climate.waterC(), climate.rain(), climate.waves(),
                Comfort.of(climate.airC(), climate.rain()), climate.note(), hazards));
    }

    private Snapshot data() {
        Snapshot current = snapshot.get();
        Instant now = clock.instant();
        if (current != null && current.loadedAt().plus(TTL).isAfter(now)) {
            return current;
        }
        Snapshot fresh = load(now);
        snapshot.set(fresh);
        return fresh;
    }

    private Snapshot load(Instant now) {
        Map<UUID, BeachInfo> beaches = new HashMap<>();
        Map<String, List<BeachInfo>> beachesByCity = new HashMap<>();
        jdbc.sql("SELECT b.*, ST_Y(b.location::geometry) AS lat, ST_X(b.location::geometry) AS lon FROM beach b ORDER BY b.name")
                .query((rs, n) -> Map.entry(rs.getString("city"), beach(rs)))
                .list().forEach(e -> {
                    beaches.put(e.getValue().id(), e.getValue());
                    beachesByCity.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                });

        Map<String, String> regions = new HashMap<>();
        jdbc.sql("SELECT country, city, region FROM climate_region")
                .query((rs, n) -> Map.entry(rs.getString("country") + "|" + rs.getString("city"), rs.getString("region")))
                .list().forEach(e -> regions.put(e.getKey(), e.getValue()));

        Map<String, ClimateRow> climate = new HashMap<>();
        jdbc.sql("SELECT * FROM climate_month").query((rs, n) -> new ClimateRow(
                        rs.getString("region"), rs.getInt("month"), rs.getInt("air_c"),
                        rs.getObject("water_c", Integer.class), RainLevel.valueOf(rs.getString("rain")),
                        rs.getString("waves") == null ? null : WaveLevel.valueOf(rs.getString("waves")),
                        rs.getString("note")))
                .list().forEach(c -> climate.put(c.region() + "|" + c.month(), c));

        Map<String, List<HazardRow>> hazards = new HashMap<>();
        jdbc.sql("SELECT region, kind, months, severity, note FROM seasonal_hazard ORDER BY severity DESC, kind")
                .query((rs, n) -> new HazardRow(rs.getString("region"), HazardKind.valueOf(rs.getString("kind")),
                        months(rs.getArray("months")), Severity.valueOf(rs.getString("severity")), rs.getString("note")))
                .list().forEach(h -> hazards.computeIfAbsent(h.region(), k -> new ArrayList<>()).add(h));
        hazards.values().forEach(list -> list.sort((a, b) -> b.severity().compareTo(a.severity())));

        log.info("travel guide loaded: beaches={} cities={} climateRows={}", beaches.size(), regions.size(), climate.size());
        Map<String, EntryInfo> entry = new HashMap<>();
        jdbc.sql("SELECT * FROM entry_rule").query((rs, n) -> Map.entry(
                        rs.getString("country") + "|" + rs.getString("city"),
                        new EntryInfo(EntryRegime.valueOf(rs.getString("regime")), rs.getObject("max_days", Integer.class),
                                rs.getBoolean("internal_passport"), rs.getString("summary"), rs.getString("details"),
                                rs.getString("source_url"), rs.getDate("checked_on").toLocalDate())))
                .list().forEach(e -> entry.put(e.getKey(), e.getValue()));

        Map<String, List<WildlifeInfo>> wildlife = new HashMap<>();
        jdbc.sql("SELECT * FROM wildlife ORDER BY kind, viewing").query((rs, n) -> Map.entry(
                        rs.getString("country") + "|" + rs.getString("city"),
                        new WildlifeInfo(Wildlife.valueOf(rs.getString("kind")), ViewingMode.valueOf(rs.getString("viewing")),
                                List.copyOf(months(rs.getArray("months"))).stream().sorted().toList(),
                                Chance.valueOf(rs.getString("chance")), rs.getString("note"))))
                .list().forEach(e -> wildlife.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        // сначала самые вероятные встречи, при равенстве — с берега (бесплатно)
        wildlife.replaceAll((k, v) -> v.stream()
                .sorted(java.util.Comparator.comparing(WildlifeInfo::chance).reversed()
                        .thenComparing(WildlifeInfo::viewing))
                .toList());

        beachesByCity.replaceAll((k, v) -> List.copyOf(v));
        return new Snapshot(now, Map.copyOf(beaches), Map.copyOf(beachesByCity), Map.copyOf(regions),
                Map.copyOf(climate), Map.copyOf(hazards), Map.copyOf(entry), Map.copyOf(wildlife));
    }

    private static BeachInfo beach(ResultSet rs) throws SQLException {
        Set<BeachActivity> activities = EnumSet.noneOf(BeachActivity.class);
        for (String a : strings(rs.getArray("activities"))) {
            parse(BeachActivity.class, a).ifPresent(activities::add);
        }
        Map<BeachActivity, List<Integer>> seasons = new EnumMap<>(BeachActivity.class);
        for (String s : strings(rs.getArray("activity_seasons"))) {
            int sep = s.indexOf(':');
            if (sep > 0) {
                parse(BeachActivity.class, s.substring(0, sep)).ifPresent(a -> seasons.put(a,
                        Arrays.stream(s.substring(sep + 1).split(",")).map(String::strip).map(Integer::valueOf).toList()));
            }
        }
        return new BeachInfo(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                BeachSurface.valueOf(rs.getString("surface")),
                rs.getBoolean("artificial_sand"),
                BeachEntry.valueOf(rs.getString("entry")),
                rs.getBoolean("depth_drop"),
                rs.getString("depth_note"),
                TideLevel.valueOf(rs.getString("tides")),
                rs.getString("tide_note"),
                rs.getInt("length_m"),
                CrowdLevel.valueOf(rs.getString("crowd")),
                activities,
                seasons,
                List.of(strings(rs.getArray("entertainment"))),
                rs.getBoolean("kids_friendly"),
                rs.getBoolean("seniors_friendly"),
                rs.getBoolean("accessible"),
                rs.getString("accessibility_note"),
                rs.getString("description"));
    }

    private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
        try {
            return Optional.of(Enum.valueOf(type, value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Set<Integer> months(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        try {
            Object raw = array.getArray();
            Set<Integer> result = new java.util.HashSet<>();
            for (Object o : (Object[]) raw) {
                result.add(((Number) o).intValue());
            }
            return result;
        } finally {
            array.free();
        }
    }

    private static String[] strings(Array array) throws SQLException {
        if (array == null) {
            return new String[0];
        }
        try {
            return (String[]) array.getArray();
        } finally {
            array.free();
        }
    }

    private record Snapshot(Instant loadedAt, Map<UUID, BeachInfo> beaches, Map<String, List<BeachInfo>> beachesByCity,
                            Map<String, String> regions,
                            Map<String, ClimateRow> climate, Map<String, List<HazardRow>> hazards,
                            Map<String, EntryInfo> entry, Map<String, List<WildlifeInfo>> wildlife) {
    }

    private record ClimateRow(String region, int month, int airC, Integer waterC, RainLevel rain, WaveLevel waves,
                              String note) {
    }

    private record HazardRow(String region, HazardKind kind, Set<Integer> months, Severity severity, String note) {
    }
}
