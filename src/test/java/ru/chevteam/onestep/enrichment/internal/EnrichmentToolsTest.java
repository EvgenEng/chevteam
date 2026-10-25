package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.geo.Geo;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.tools.BuiltIns;
import ru.chevteam.onestep.enrichment.internal.tools.Characteristic;
import ru.chevteam.onestep.enrichment.internal.tools.Computed;
import ru.chevteam.onestep.enrichment.internal.tools.Recipe;
import ru.chevteam.onestep.enrichment.internal.tools.Site;
import ru.chevteam.onestep.enrichment.internal.tools.TextRecipes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Синтетическая карта вокруг отеля: берег в ~110 м к югу, главная улица в ~20 м, лестница к морю,
 * бар, стройплощадка. Каждая характеристика должна «увидеть» ровно то, что нарисовано.
 */
class EnrichmentToolsTest {

    static final Point HOTEL = new Point(44.5000, 34.1700);
    /** 0,001° широты ≈ 111 м. */
    static final double LAT_M = 0.001 / 111.0;

    static Point at(double northMeters, double eastMeters) {
        return new Point(HOTEL.lat() + northMeters * LAT_M,
                HOTEL.lon() + eastMeters * LAT_M / Math.cos(Math.toRadians(HOTEL.lat())));
    }

    static OsmElement way(long id, Map<String, String> tags, Point... points) {
        return new OsmElement("way", id, tags, List.of(points));
    }

    static final OsmSnapshot MAP = new OsmSnapshot(List.of(
            way(1, Map.of("natural", "coastline"), at(-110, -500), at(-110, 500)),
            way(2, Map.of("natural", "beach", "surface", "pebbles", "name", "Городской пляж"),
                    at(-105, -100), at(-105, 100), at(-125, 100), at(-125, -100), at(-105, -100)),
            way(3, Map.of("highway", "primary", "name", "улица Рузвельта"), at(20, -400), at(20, 400)),
            way(4, Map.of("highway", "steps"), at(-30, 5), at(-70, 5)),
            new OsmElement("node", 5, Map.of("amenity", "bar", "name", "Старый погреб"), List.of(at(50, 30))),
            way(6, Map.of("landuse", "construction"), at(150, 150), at(150, 250), at(250, 250), at(250, 150), at(150, 150)),
            new OsmElement("node", 7, Map.of("amenity", "pharmacy", "name", "Аптека №1"), List.of(at(0, 180)))), null);

    static final Site SITE = new Site(UUID.randomUUID(), "Тест", HOTEL);

    static Computed compute(String code) {
        Characteristic c = BuiltIns.all().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
        return c.compute(SITE, MAP).orElseThrow();
    }

    @Test
    void geometry() {
        assertThat(Geo.meters(HOTEL, at(100, 0))).isCloseTo(100, within(1.0));
        assertThat(Geo.metersToLine(HOTEL, List.of(at(-50, -100), at(-50, 100)))).isCloseTo(50, within(1.0));
        List<Point> square = List.of(at(-10, -10), at(-10, 10), at(10, 10), at(10, -10), at(-10, -10));
        assertThat(Geo.inside(HOTEL, square)).isTrue();
        assertThat(Geo.inside(at(30, 0), square)).isFalse();
    }

    @Test
    void osmFilter() {
        OsmFilter f = OsmFilter.parse("amenity=cafe|bar;shop=*");
        assertThat(f.test(new OsmElement("node", 1, Map.of("amenity", "bar"), List.of(HOTEL)))).isTrue();
        assertThat(f.test(new OsmElement("node", 1, Map.of("shop", "bakery"), List.of(HOTEL)))).isTrue();
        assertThat(f.test(new OsmElement("node", 1, Map.of("amenity", "bank"), List.of(HOTEL)))).isFalse();
        assertThatThrownBy(() -> OsmFilter.parse("DROP TABLE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seaBeachAndStairs() {
        Computed sea = compute(BuiltIns.SEA_DISTANCE);
        assertThat(sea.level()).isEqualTo("FIRST_LINE");
        assertThat(sea.number()).as(sea.toString()).isBetween(100.0, 112.0);
        assertThat(compute(BuiltIns.BEACH_SURFACE).level()).isEqualTo("PEBBLE");
        Computed stairs = compute(BuiltIns.STAIRS_TO_SEA);
        assertThat(stairs.level()).isEqualTo("SOME");
        assertThat(stairs.value()).contains("лестницы");
    }

    @Test
    void noiseNightlifeAndConstruction() {
        Computed noise = compute(BuiltIns.ROAD_NOISE);
        assertThat(noise.level()).isEqualTo("HIGH");
        assertThat(noise.evidence()).contains("улица Рузвельта").contains("20 м");
        assertThat(compute(BuiltIns.NIGHTLIFE).level()).isEqualTo("SOME");
        assertThat(compute(BuiltIns.CONSTRUCTION).level()).isEqualTo("YES");
        assertThat(compute(BuiltIns.GREENERY).level()).isEqualTo("NO");
    }

    @Test
    void characteristicFromText() {
        Recipe pharmacy = TextRecipes.parse("аптека в 5 минутах").orElseThrow();
        assertThat(pharmacy.tool()).isEqualTo(Recipe.Tool.NEAREST);
        assertThat(pharmacy.filter()).isEqualTo("amenity=pharmacy");
        assertThat(pharmacy.radius()).isEqualTo(300);
        assertThat(pharmacy.characteristic().compute(SITE, MAP).orElseThrow().level()).isEqualTo("YES");

        Recipe noClubs = TextRecipes.parse("нет ночных клубов рядом").orElseThrow();
        assertThat(noClubs.tool()).isEqualTo(Recipe.Tool.ABSENT);
        assertThat(noClubs.characteristic().compute(SITE, MAP).orElseThrow().level()).isEqualTo("YES");

        Recipe manyCafes = TextRecipes.parse("много кафе рядом").orElseThrow();
        assertThat(manyCafes.tool()).isEqualTo(Recipe.Tool.COUNT);
        assertThat(manyCafes.min()).isEqualTo(5);

        assertThat(TextRecipes.parse("парковка у отеля").orElseThrow().filter()).isEqualTo("amenity=parking");
        assertThat(TextRecipes.parse("красивый вид на закат")).isEmpty();
    }
}
