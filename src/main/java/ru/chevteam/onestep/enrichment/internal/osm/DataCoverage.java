package ru.chevteam.onestep.enrichment.internal.osm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Какие объекты OSM реально выгружены в снимок. Нужна, чтобы отличать «аптеки рядом нет» от «аптеки мы не скачивали».
 * <p>
 * Определять покрытие по самим данным нельзя: объект с тегом может попасть в снимок случайно (храм-достопримечательность
 * приходит запросом «tourism=attraction»), а ноль парковок значит лишь, что парковки не запрашивали.
 * Поэтому покрытие задаётся явно — теми же фильтрами, что и запросы выгрузки.
 */
public final class DataCoverage {

    private final Map<String, Set<String>> covered;

    private DataCoverage(Map<String, Set<String>> covered) {
        this.covered = covered;
    }

    public static DataCoverage of(List<String> filters) {
        DataCoverage c = new DataCoverage(new LinkedHashMap<>());
        for (String f : filters) {
            c = c.with(f);
        }
        return c;
    }

    /** Покрытие с добавленным фильтром — после того как админ одобрил и мы скачали новый источник. */
    public DataCoverage with(String filter) {
        Map<String, Set<String>> next = new LinkedHashMap<>();
        covered.forEach((k, v) -> next.put(k, v.isEmpty() ? Set.of() : new TreeSet<>(v)));
        OsmFilter.parse(filter).rules().forEach((key, values) -> {
            Set<String> have = next.get(key);
            if (values.isEmpty() || (have != null && have.isEmpty())) {
                next.put(key, Set.of());
            } else {
                next.computeIfAbsent(key, k -> new TreeSet<>()).addAll(values);
            }
        });
        return new DataCoverage(next);
    }

    /** Чего из фильтра нет в снимке — в том же синтаксисе фильтра; пусто — всё уже есть. */
    public Optional<String> missing(String filter) {
        List<String> parts = new ArrayList<>();
        OsmFilter.parse(filter).rules().forEach((key, values) -> {
            Set<String> have = covered.get(key);
            if (have != null && have.isEmpty()) {
                return; // ключ выгружен целиком
            }
            if (values.isEmpty()) {
                parts.add(key + "=*");
                return;
            }
            List<String> absent = values.stream().filter(v -> have == null || !have.contains(v)).toList();
            if (!absent.isEmpty()) {
                parts.add(key + "=" + String.join("|", absent));
            }
        });
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(";", parts));
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        covered.forEach((k, v) -> parts.add(k + "=" + (v.isEmpty() ? "*" : String.join("|", v))));
        return String.join(";", parts);
    }
}
