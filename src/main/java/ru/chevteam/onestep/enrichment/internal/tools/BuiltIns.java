package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.geo.Geo;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot.Nearby;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Встроенные характеристики: каждая — прозрачное правило над OpenStreetMap, без «угадывания» моделью.
 * Пороги подобраны под курортный город: 150 м — первая линия, 15 минут — комфортная прогулка.
 */
public final class BuiltIns {

    public static final String SEA_DISTANCE = "SEA_DISTANCE";
    public static final String BEACH_SURFACE = "BEACH_SURFACE";
    public static final String STAIRS_TO_SEA = "STAIRS_TO_SEA";
    public static final String ROAD_NOISE = "ROAD_NOISE";
    public static final String NIGHTLIFE = "NIGHTLIFE";
    public static final String CONSTRUCTION = "CONSTRUCTION";
    public static final String FOOD_WALK = "FOOD_WALK";
    public static final String GROCERY = "GROCERY";
    public static final String BUS_STOP = "BUS_STOP";
    public static final String GREENERY = "GREENERY";
    public static final String SIGHTS = "SIGHTS";

    static final OsmFilter SEA = OsmFilter.parse("natural=coastline;natural=beach;leisure=beach_resort");
    static final OsmFilter BEACH = OsmFilter.parse("natural=beach;leisure=beach_resort");
    static final OsmFilter STEPS = OsmFilter.parse("highway=steps");
    static final OsmFilter ROADS = OsmFilter.parse("highway=motorway|trunk|primary|secondary|tertiary");
    static final OsmFilter BARS = OsmFilter.parse("amenity=bar|pub|nightclub");
    static final OsmFilter BUILDING_SITE = OsmFilter.parse("landuse=construction");
    static final OsmFilter FOOD = OsmFilter.parse("amenity=cafe|restaurant|fast_food");
    static final OsmFilter SHOPS = OsmFilter.parse("shop=supermarket|convenience|marketplace|mall");
    static final OsmFilter STOPS = OsmFilter.parse("highway=bus_stop;public_transport=platform");
    static final OsmFilter PARKS = OsmFilter.parse("leisure=park|garden|nature_reserve");
    static final OsmFilter SIGHT = OsmFilter.parse("tourism=attraction|museum|viewpoint;historic=castle|palace|monument");

    /** Вклад дороги в шум: магистраль слышно дальше, чем местную улицу. */
    static final Map<String, Double> ROAD_WEIGHT = Map.of(
            "motorway", 1.0, "trunk", 0.95, "primary", 0.8, "secondary", 0.55, "tertiary", 0.35);
    static final Map<String, String> ROAD_KIND = Map.of(
            "motorway", "магистраль", "trunk", "трасса", "primary", "главная улица", "secondary", "оживлённая улица",
            "tertiary", "улица");

    private BuiltIns() {
    }

    public static List<Characteristic> all() {
        return List.of(new SeaDistance(), new BeachSurface(), new StairsToSea(), new RoadNoise(), new Nightlife(),
                new Construction(), new FoodWalk(), new Grocery(), new BusStop(), new Greenery(), new Sights());
    }

    // ------------------------------------------------------------------ море и пляж

    static final class SeaDistance implements Characteristic {
        public String code() { return SEA_DISTANCE; }
        public String title() { return "До моря"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            return osm.nearest(site.location(), 5000, SEA).map(n -> {
                int walk = Geo.walkMinutes(n.meters());
                String level = n.meters() <= 150 ? "FIRST_LINE" : n.meters() <= 600 ? "NEAR" : n.meters() <= 1500 ? "WALK" : "FAR";
                String value = n.meters() <= 1500
                        ? Format.meters(n.meters()) + " · " + Format.minutes(walk)
                        : Format.meters(n.meters()) + " — пешком далеко";
                String what = "coastline".equals(n.element().tag("natural")) ? "береговая линия"
                        : Format.label(n.element(), "пляж");
                return new Computed(value, level, Math.rint(n.meters()), 0.9,
                        "ближайшая береговая линия или пляж OSM; пешком — по прямой × 1,25, 75 м/мин",
                        what + " (" + n.element().ref() + ") — " + Format.meters(n.meters()));
            });
        }
    }

    static final class BeachSurface implements Characteristic {
        private static final Map<String, String[]> SURFACE = Map.of(
                "sand", new String[] {"SAND", "песок"}, "fine_gravel", new String[] {"PEBBLE", "мелкая галька"},
                "pebbles", new String[] {"PEBBLE", "галька"}, "pebblestone", new String[] {"PEBBLE", "галька"},
                "shingle", new String[] {"PEBBLE", "крупная галька"}, "gravel", new String[] {"PEBBLE", "галька"},
                "concrete", new String[] {"CONCRETE", "бетонные плиты"}, "paving_stones", new String[] {"CONCRETE", "плитка"},
                "rock", new String[] {"ROCK", "камни и скалы"}, "stone", new String[] {"ROCK", "камни"});

        public String code() { return BEACH_SURFACE; }
        public String title() { return "Покрытие пляжа"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            List<Nearby> beaches = osm.within(site.location(), 1500, BEACH);
            if (beaches.isEmpty()) {
                return Optional.empty();
            }
            for (Nearby b : beaches) {
                String tag = b.element().tag("surface");
                String[] s = tag == null ? null : SURFACE.get(tag);
                if (s != null) {
                    return Optional.of(new Computed(s[1], s[0], null, b == beaches.get(0) ? 0.8 : 0.6,
                            "тег surface ближайшего размеченного пляжа OSM",
                            Format.label(b.element(), "пляж") + ", surface=" + b.element().tag("surface") + ", "
                                    + Format.meters(b.meters())));
                }
            }
            Nearby b = beaches.get(0);
            return Optional.of(new Computed("покрытие не размечено", "UNKNOWN", null, 0.2,
                    "тег surface ближайшего пляжа OSM", Format.label(b.element(), "пляж") + " без тега surface"));
        }
    }

    /**
     * Спуск к морю по лестницам: лестницы OSM в коридоре 60 м вдоль прямой «отель — ближайшая точка берега».
     * На Южном берегу Крыма это главный скрытый минус «близкого» отеля.
     */
    static final class StairsToSea implements Characteristic {
        public String code() { return STAIRS_TO_SEA; }
        public String title() { return "Лестницы к морю"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            Optional<Nearby> sea = osm.nearest(site.location(), 2000, SEA);
            if (sea.isEmpty()) {
                return Optional.empty();
            }
            Point target = closestVertex(site.location(), sea.get().element());
            List<Point> corridor = List.of(site.location(), target);
            double total = 0;
            int flights = 0;
            for (Nearby s : osm.within(site.location(), sea.get().meters() + 100, STEPS)) {
                boolean onTheWay = s.element().geometry().stream().anyMatch(p -> Geo.metersToLine(p, corridor) <= 60);
                if (onTheWay) {
                    total += Geo.length(s.element().geometry());
                    flights++;
                }
            }
            String level = total == 0 ? "NONE" : total < 60 ? "SOME" : "MANY";
            String value = total == 0 ? "без лестниц по данным карты"
                    : "лестницы ≈" + Format.meters(total) + " (" + flights + " " + plural(flights, "пролёт", "пролёта", "пролётов") + ")";
            return Optional.of(new Computed(value, level, Math.rint(total), total == 0 ? 0.5 : 0.75,
                    "лестницы OSM (highway=steps) в коридоре 60 м по пути к берегу",
                    total == 0 ? "на пути к морю лестниц не размечено" : flights + " лестниц OSM, всего " + Format.meters(total)));
        }

        private static Point closestVertex(Point from, OsmElement e) {
            Point best = e.geometry().get(0);
            double d = Double.POSITIVE_INFINITY;
            for (Point p : e.geometry()) {
                double m = Geo.meters(from, p);
                if (m < d) {
                    d = m;
                    best = p;
                }
            }
            return best;
        }
    }

    // ------------------------------------------------------------------ шум и окружение

    /**
     * Шум дорог: вклад каждой дороги = вес класса × затухание с расстоянием (до 30 м — полный, к 250 м — ноль).
     * Берём максимум: шумит самая громкая дорога, а не сумма тихих улочек.
     */
    static final class RoadNoise implements Characteristic {
        public String code() { return ROAD_NOISE; }
        public String title() { return "Шум дорог"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            double score = 0;
            Nearby loudest = null;
            for (Nearby r : osm.within(site.location(), 250, ROADS)) {
                String cls = r.element().tag("highway");
                double w = cls == null ? 0.3 : ROAD_WEIGHT.getOrDefault(cls, 0.3);
                double fade = r.meters() <= 30 ? 1 : Math.max(0, 1 - (r.meters() - 30) / 220);
                if (w * fade > score) {
                    score = w * fade;
                    loudest = r;
                }
            }
            String level = score < 0.25 ? "LOW" : score < 0.55 ? "MEDIUM" : "HIGH";
            String value = switch (level) {
                case "LOW" -> "тихо";
                case "MEDIUM" -> "слышно улицу";
                default -> "шумная дорога рядом";
            };
            String evidence = loudest == null ? "в 250 м нет улиц крупнее местных проездов"
                    : roadName(loudest.element()) + " — " + Format.meters(loudest.meters());
            return Optional.of(new Computed(value, level, Math.round(score * 100) / 100.0, 0.8,
                    "дороги OSM в радиусе 250 м: вес класса дороги × затухание с расстоянием", evidence));
        }
    }

    static String roadName(OsmElement road) {
        String cls = road.tag("highway");
        String kind = cls == null ? "дорога" : ROAD_KIND.getOrDefault(cls, "дорога");
        String name = road.name();
        String ref = road.tag("ref");
        return (name != null ? name : ref != null ? ref : "без названия") + " (" + kind + ")";
    }

    static final class Nightlife implements Characteristic {
        public String code() { return NIGHTLIFE; }
        public String title() { return "Бары и клубы рядом"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            // собственный лобби-бар отеля — не «бар под окнами»
            List<Nearby> bars = osm.within(site.location(), 200, BARS).stream()
                    .filter(b -> b.element().name() == null || !b.element().name().toLowerCase().contains("лобби"))
                    .toList();
            boolean club = bars.stream().anyMatch(b -> "nightclub".equals(b.element().tag("amenity")));
            String level = bars.isEmpty() ? "NONE" : club || bars.size() >= 3 ? "HIGH" : "SOME";
            String value = bars.isEmpty() ? "в 200 м баров и клубов нет"
                    : (club ? "ночной клуб рядом" : bars.size() + " " + plural(bars.size(), "бар", "бара", "баров") + " в 200 м");
            return Optional.of(new Computed(value, level, (double) bars.size(), 0.7,
                    "бары, пабы и ночные клубы OSM в радиусе 200 м", names(bars, "бар", 3)));
        }
    }

    static final class Construction implements Characteristic {
        public String code() { return CONSTRUCTION; }
        public String title() { return "Стройка рядом"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            Optional<Nearby> site300 = osm.nearest(site.location(), 300, BUILDING_SITE);
            return Optional.of(site300
                    .map(n -> new Computed("стройка в " + Format.meters(n.meters()), "YES", Math.rint(n.meters()), 0.6,
                            "участки landuse=construction OSM в радиусе 300 м",
                            Format.label(n.element(), "стройплощадка") + " (" + n.element().ref() + ")"))
                    .orElse(new Computed("строек рядом не отмечено", "NO", null, 0.5,
                            "участки landuse=construction OSM в радиусе 300 м", "в 300 м стройплощадок на карте нет")));
        }
    }

    // ------------------------------------------------------------------ инфраструктура

    static final class FoodWalk implements Characteristic {
        public String code() { return FOOD_WALK; }
        public String title() { return "Кафе в 5 минутах"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            List<Nearby> food = osm.within(site.location(), 400, FOOD);
            String level = food.size() >= 5 ? "MANY" : food.isEmpty() ? "NONE" : "SOME";
            String value = food.isEmpty() ? "в 5 минутах кафе нет" : food.size() + " " + plural(food.size(), "кафе", "кафе", "кафе") + " в 5 минутах";
            return Optional.of(new Computed(value, level, (double) food.size(), 0.75,
                    "кафе, рестораны и фастфуд OSM в радиусе 400 м", names(food, "кафе", 3)));
        }
    }

    static final class Grocery implements Characteristic {
        public String code() { return GROCERY; }
        public String title() { return "Продуктовый магазин"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            return Optional.of(osm.nearest(site.location(), 1500, SHOPS)
                    .map(n -> new Computed(Format.meters(n.meters()) + " · " + Format.minutes(Geo.walkMinutes(n.meters())),
                            n.meters() <= 500 ? "NEAR" : "WALK", Math.rint(n.meters()), 0.75,
                            "ближайший супермаркет, магазин у дома или рынок OSM", Format.label(n.element(), "магазин")))
                    .orElse(new Computed("в 1,5 км магазинов нет", "FAR", null, 0.6,
                            "ближайший супермаркет, магазин у дома или рынок OSM", "магазинов в 1,5 км на карте нет")));
        }
    }

    static final class BusStop implements Characteristic {
        public String code() { return BUS_STOP; }
        public String title() { return "Остановка транспорта"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            return Optional.of(osm.nearest(site.location(), 1500, STOPS)
                    .map(n -> new Computed(Format.meters(n.meters()) + " · " + Format.minutes(Geo.walkMinutes(n.meters())),
                            n.meters() <= 400 ? "NEAR" : "WALK", Math.rint(n.meters()), 0.8,
                            "ближайшая остановка автобуса или троллейбуса OSM", Format.label(n.element(), "остановка")))
                    .orElse(new Computed("остановок в 1,5 км нет", "FAR", null, 0.6,
                            "ближайшая остановка автобуса или троллейбуса OSM", "остановок в 1,5 км на карте нет")));
        }
    }

    static final class Greenery implements Characteristic {
        public String code() { return GREENERY; }
        public String title() { return "Парк рядом"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            return Optional.of(osm.nearest(site.location(), 600, PARKS)
                    .map(n -> new Computed(n.meters() == 0 ? "отель в парке" : Format.label(n.element(), "парк") + " — "
                            + Format.minutes(Geo.walkMinutes(n.meters())), "YES", Math.rint(n.meters()), 0.75,
                            "ближайший парк, сад или заповедник OSM в радиусе 600 м", n.element().ref()))
                    .orElse(new Computed("парков рядом нет", "NO", null, 0.6,
                            "ближайший парк, сад или заповедник OSM в радиусе 600 м", "в 600 м парков на карте нет")));
        }
    }

    static final class Sights implements Characteristic {
        public String code() { return SIGHTS; }
        public String title() { return "Что посмотреть рядом"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            List<Nearby> named = osm.within(site.location(), 2000, SIGHT).stream().filter(n -> n.element().name() != null).toList();
            Set<String> uniq = new LinkedHashSet<>();
            for (Nearby n : named) {
                uniq.add(n.element().name());
            }
            if (uniq.isEmpty()) {
                return Optional.of(new Computed("в 2 км ничего не отмечено", "NONE", 0.0, 0.5,
                        "достопримечательности, музеи и смотровые OSM в радиусе 2 км", "—"));
            }
            List<String> top = new ArrayList<>();
            for (Nearby n : named) {
                if (top.size() < 3 && !top.contains(n.element().name())) {
                    top.add(n.element().name() + " — " + Format.meters(n.meters()));
                }
            }
            return Optional.of(new Computed(uniq.size() + " " + plural(uniq.size(), "место", "места", "мест") + " в 2 км",
                    uniq.size() >= 5 ? "MANY" : "SOME", (double) uniq.size(), 0.7,
                    "достопримечательности, музеи и смотровые OSM в радиусе 2 км", String.join("; ", top)));
        }
    }

    // ------------------------------------------------------------------ помощники

    /** Ближайшие по именам без повторов: одно кафе часто нарисовано и точкой, и контуром здания. */
    static String names(List<Nearby> list, String kind, int limit) {
        if (list.isEmpty()) {
            return "—";
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Nearby n : list) {
            String label = Format.label(n.element(), kind);
            if (!label.endsWith("без названия") && !seen.add(label.toLowerCase())) {
                continue;
            }
            if (out.size() < limit) {
                out.add(label + " — " + Format.meters(n.meters()));
            }
        }
        return String.join("; ", out) + (list.size() > limit ? " и ещё " + (list.size() - limit) : "");
    }

    static String plural(int n, String one, String few, String many) {
        int m10 = n % 10;
        int m100 = n % 100;
        return m10 == 1 && m100 != 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many;
    }
}
