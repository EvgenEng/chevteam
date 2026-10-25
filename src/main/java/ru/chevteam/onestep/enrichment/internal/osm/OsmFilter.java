package ru.chevteam.onestep.enrichment.internal.osm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Отбор объектов по тегам: {@code "amenity=cafe|restaurant"}, {@code "highway=steps"}, {@code "natural=beach"}.
 * Несколько условий через «;» — достаточно любого: {@code "shop=supermarket;shop=convenience"}.
 * Это же текстовое описание пишет LLM, когда собирает новую характеристику.
 */
public final class OsmFilter implements Predicate<OsmElement> {

    private final List<Rule> rules;
    private final String text;

    private OsmFilter(List<Rule> rules, String text) {
        this.rules = rules;
        this.text = text;
    }

    public static OsmFilter parse(String text) {
        Objects.requireNonNull(text, "filter");
        List<Rule> rules = Arrays.stream(text.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(OsmFilter::rule)
                .toList();
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Пустой фильтр OSM");
        }
        return new OsmFilter(rules, text.strip());
    }

    private static Rule rule(String part) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("Фильтр OSM должен иметь вид ключ=значение: " + part);
        }
        String key = part.substring(0, eq).strip();
        String values = part.substring(eq + 1).strip();
        if (!key.matches("[a-z_:]{2,40}")) {
            throw new IllegalArgumentException("Недопустимый ключ OSM: " + key);
        }
        Set<String> set = values.equals("*") ? Set.of()
                : Arrays.stream(values.split("\\|")).map(String::strip).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
        return new Rule(key, set);
    }

    @Override
    public boolean test(OsmElement e) {
        for (Rule r : rules) {
            String v = e.tag(r.key());
            if (v != null && (r.values().isEmpty() || r.values().contains(v))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return text;
    }

    private record Rule(String key, Set<String> values) {
    }
}
