package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Настоящие места рядом с реальным отелем — из OpenStreetMap, а не из демо-справочника города:
 * что посмотреть (до 2 км) и где поесть (до 800 м). Только объекты с названием: «кафе без названия» гостю не поможет.
 */
public final class NearbyPlaces {

    public static final String SEE = "SEE";
    public static final String FOOD = "FOOD";

    static final int SEE_RADIUS = 2000;
    static final int FOOD_RADIUS = 800;
    static final int LIMIT = 8;

    private static final OsmFilter SIGHTS = OsmFilter.parse(
            "tourism=attraction|museum|viewpoint;historic=castle|palace|monument|memorial;leisure=park|garden|nature_reserve");
    private static final OsmFilter FOOD_FILTER = OsmFilter.parse("amenity=cafe|restaurant|fast_food");
    private static final Pattern HOURS = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})");

    private static final Map<String, String> CUISINE = Map.ofEntries(
            Map.entry("regional", "местная"), Map.entry("russian", "русская"), Map.entry("ukrainian", "украинская"),
            Map.entry("crimean_tatar", "крымскотатарская"), Map.entry("tatar", "татарская"), Map.entry("uzbek", "узбекская"),
            Map.entry("georgian", "грузинская"), Map.entry("caucasian", "кавказская"), Map.entry("armenian", "армянская"),
            Map.entry("italian", "итальянская"), Map.entry("pizza", "пицца"), Map.entry("japanese", "японская"),
            Map.entry("sushi", "суши"), Map.entry("chinese", "китайская"), Map.entry("seafood", "морепродукты"),
            Map.entry("fish", "рыба"), Map.entry("burger", "бургеры"), Map.entry("coffee_shop", "кофейня"),
            Map.entry("international", "разная кухня"), Map.entry("european", "европейская"), Map.entry("shawarma", "шаурма"),
            Map.entry("kebab", "кебаб"), Map.entry("bakery", "выпечка"), Map.entry("ice_cream", "мороженое"),
            Map.entry("cake", "десерты"), Map.entry("chicken", "курица"), Map.entry("grill", "гриль"));

    private NearbyPlaces() {
    }

    /**
     * @param kind     для «что посмотреть»: SIGHT, MUSEUM, VIEWPOINT, PARK, NATURE — как в справочнике мест поиска
     * @param meals    BREAKFAST / DINNER — только если это следует из часов работы
     * @param diets    VEGETARIAN / VEGAN / HALAL — из тегов diet:*
     */
    public record Place(String list, String kind, String name, int meters, String ref, String cuisine, Set<String> meals,
                       Set<String> diets, String note) {
    }

    public static List<Place> find(Site site, OsmSnapshot osm) {
        List<Place> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OsmSnapshot.Nearby n : osm.within(site.location(), SEE_RADIUS, SIGHTS)) {
            if (plaque(n.element())) {
                continue; // мемориальные доски — не то, ради чего идут гулять
            }
            String name = sightName(n.element());
            if (name == null || !seen.add(SEE + name.toLowerCase(Locale.ROOT)) || count(result, SEE) >= LIMIT) {
                continue;
            }
            result.add(new Place(SEE, seeKind(n.element()), name, (int) Math.round(n.meters()), ref(n.element()), null, Set.of(),
                    Set.of(), null));
        }
        for (OsmSnapshot.Nearby n : osm.within(site.location(), FOOD_RADIUS, FOOD_FILTER)) {
            String name = name(n.element());
            if (name == null || !seen.add(FOOD + name.toLowerCase(Locale.ROOT)) || count(result, FOOD) >= LIMIT) {
                continue;
            }
            OsmElement e = n.element();
            String hours = e.tag("opening_hours");
            result.add(new Place(FOOD, "FOOD", name, (int) Math.round(n.meters()), ref(e), cuisine(e), meals(hours), diets(e),
                    hours == null ? null : "часы: " + hours.replace("Mo", "пн").replace("Tu", "вт").replace("We", "ср")
                            .replace("Th", "чт").replace("Fr", "пт").replace("Sa", "сб").replace("Su", "вс")));
        }
        return result;
    }

    private static long count(List<Place> places, String list) {
        return places.stream().filter(p -> p.list().equals(list)).count();
    }

    private static String name(OsmElement e) {
        String n = e.name();
        return n == null || n.isBlank() ? null : n.strip();
    }

    /** Памятники в OSM подписаны надписью с постамента — «Ф. Д. Рузвельту»; гостю понятнее «Памятник «Ф. Д. Рузвельту»». */
    static String sightName(OsmElement e) {
        String n = name(e);
        if (n == null) {
            return null;
        }
        String historic = e.tag("historic");
        String lower = n.toLowerCase(Locale.ROOT);
        if (("monument".equals(historic) || "memorial".equals(historic))
                && !lower.contains("памятник") && !lower.contains("мемориал") && !lower.contains("монумент")
                && !lower.contains("обелиск") && !lower.contains("стела")) {
            return "Памятник «" + n.replace("«", "").replace("»", "") + "»";
        }
        return n;
    }

    private static boolean plaque(OsmElement e) {
        String memorial = e.tag("memorial");
        return "memorial".equals(e.tag("historic")) && e.tag("tourism") == null
                && (memorial == null || "plaque".equals(memorial) || "blue_plaque".equals(memorial) || "stone".equals(memorial));
    }

    private static String ref(OsmElement e) {
        return e.type() + "/" + e.id();
    }

    static String seeKind(OsmElement e) {
        if ("museum".equals(e.tag("tourism"))) {
            return "MUSEUM";
        }
        if ("viewpoint".equals(e.tag("tourism"))) {
            return "VIEWPOINT";
        }
        if ("nature_reserve".equals(e.tag("leisure"))) {
            return "NATURE";
        }
        if (e.tag("leisure") != null) {
            return "PARK";
        }
        return "SIGHT";
    }

    static String cuisine(OsmElement e) {
        String tag = e.tag("cuisine");
        if (tag != null) {
            List<String> parts = new ArrayList<>();
            for (String c : tag.split("[;,]")) {
                String ru = CUISINE.get(c.strip().toLowerCase(Locale.ROOT));
                if (ru != null && !parts.contains(ru)) {
                    parts.add(ru);
                }
            }
            if (!parts.isEmpty()) {
                return String.join(", ", parts.subList(0, Math.min(2, parts.size())));
            }
        }
        return switch (String.valueOf(e.tag("amenity"))) {
            case "restaurant" -> "ресторан";
            case "fast_food" -> "фастфуд";
            default -> "кафе";
        };
    }

    /** Завтраки — если открыто к 9 утра, ужины — если работает до 21 и позже. Нет часов — ничего не утверждаем. */
    static Set<String> meals(String hours) {
        if (hours == null) {
            return Set.of();
        }
        if (hours.contains("24/7")) {
            return Set.of("BREAKFAST", "DINNER");
        }
        Set<String> meals = new LinkedHashSet<>();
        Matcher m = HOURS.matcher(hours);
        while (m.find()) {
            int open = Integer.parseInt(m.group(1));
            int close = Integer.parseInt(m.group(3));
            if (open <= 9) {
                meals.add("BREAKFAST");
            }
            if (close >= 21 || close < open) {
                meals.add("DINNER");
            }
        }
        return meals;
    }

    static Set<String> diets(OsmElement e) {
        Set<String> d = new LinkedHashSet<>();
        if (yes(e.tag("diet:vegetarian"))) {
            d.add("VEGETARIAN");
        }
        if (yes(e.tag("diet:vegan"))) {
            d.add("VEGAN");
        }
        if (yes(e.tag("diet:halal"))) {
            d.add("HALAL");
        }
        return d;
    }

    private static boolean yes(String v) {
        return "yes".equals(v) || "only".equals(v);
    }
}
