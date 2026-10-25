package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.tools.NearbyPlaces;
import ru.chevteam.onestep.enrichment.internal.tools.Site;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Места рядом с реальным отелем — только из карты и только с названием. */
class NearbyPlacesTest {

    static final Point HOTEL = new Point(44.4963, 34.1717);

    static OsmElement node(long id, double dLat, Map<String, String> tags) {
        return new OsmElement("node", id, tags, List.of(new Point(HOTEL.lat() + dLat, HOTEL.lon())));
    }

    @Test
    void realNamesDistancesAndHonestMeals() {
        OsmSnapshot osm = new OsmSnapshot(List.of(
                node(1, 0.0003, Map.of("amenity", "cafe", "name", "Кипарис", "opening_hours", "Mo-Su 12:00-23:00",
                        "cuisine", "regional;grill")),
                node(2, 0.0005, Map.of("amenity", "restaurant", "name", "Утро", "opening_hours", "Mo-Su 08:00-16:00",
                        "diet:vegetarian", "yes")),
                node(3, 0.0006, Map.of("amenity", "cafe")), // без названия — пропускаем
                node(4, 0.0007, Map.of("amenity", "fast_food", "name", "Чебуречная")),
                node(5, 0.0010, Map.of("historic", "monument", "name", "Ф. Д. Рузвельту")),
                node(6, 0.0011, Map.of("historic", "memorial", "name", "Здесь жил поэт", "memorial", "plaque")),
                node(7, 0.0020, Map.of("tourism", "museum", "name", "Дом-музей А. П. Чехова")),
                node(8, 0.0300, Map.of("tourism", "museum", "name", "Слишком далеко"))), null);

        List<NearbyPlaces.Place> places = NearbyPlaces.find(new Site(UUID.randomUUID(), "Отель", HOTEL), osm);

        assertThat(places).filteredOn(p -> p.list().equals(NearbyPlaces.FOOD)).extracting(NearbyPlaces.Place::name)
                .containsExactly("Кипарис", "Утро", "Чебуречная");
        NearbyPlaces.Place kiparis = places.stream().filter(p -> p.name().equals("Кипарис")).findFirst().orElseThrow();
        assertThat(kiparis.cuisine()).isEqualTo("местная, гриль");
        assertThat(kiparis.meals()).containsExactly("DINNER");
        assertThat(kiparis.meters()).isBetween(25, 40);
        NearbyPlaces.Place utro = places.stream().filter(p -> p.name().equals("Утро")).findFirst().orElseThrow();
        assertThat(utro.meals()).containsExactly("BREAKFAST");
        assertThat(utro.diets()).containsExactly("VEGETARIAN");
        // нет часов работы — про завтраки и ужины ничего не утверждаем
        assertThat(places.stream().filter(p -> p.name().equals("Чебуречная")).findFirst().orElseThrow().meals()).isEmpty();

        assertThat(places).filteredOn(p -> p.list().equals(NearbyPlaces.SEE)).extracting(NearbyPlaces.Place::name)
                .containsExactly("Памятник «Ф. Д. Рузвельту»", "Дом-музей А. П. Чехова");
        assertThat(places).filteredOn(p -> p.name().contains("Чехова")).singleElement()
                .satisfies(p -> assertThat(p.kind()).isEqualTo("MUSEUM"));
    }
}
