package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.osm.DataCoverage;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OverpassQuery;
import ru.chevteam.onestep.enrichment.internal.tools.TextRecipes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Агент данных: отличает «рядом нет» от «мы этого не скачивали» и собирает безопасный запрос к Overpass. */
class DataAgentTest {

    static DataCoverage regionCoverage() throws IOException {
        try (InputStream in = DataAgentTest.class.getResourceAsStream("/osm/yalta-alushta/coverage.txt")) {
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
            return DataCoverage.of(lines);
        }
    }

    @Test
    void whatTheSnapshotHasIsNotAskedAgain() throws IOException {
        DataCoverage c = regionCoverage();
        assertThat(c.missing("amenity=pharmacy")).isEmpty();
        assertThat(c.missing("amenity=nightclub")).isEmpty();
        assertThat(c.missing("highway=steps")).isEmpty();
        assertThat(c.missing("natural=beach;leisure=beach_resort")).isEmpty();
    }

    @Test
    void missingObjectsAreNamedPrecisely() throws IOException {
        DataCoverage c = regionCoverage();
        assertThat(c.missing("leisure=playground")).contains("leisure=playground");
        assertThat(c.missing("amenity=parking")).contains("amenity=parking");
        // клиники есть, врачей частной практики не выгружали — докачать только их
        assertThat(c.missing("amenity=hospital|clinic|doctors")).contains("amenity=doctors");
        assertThat(c.missing("shop=*")).contains("shop=*");
    }

    @Test
    void everyDictionaryWordIsEitherCoveredOrAsked() throws IOException {
        DataCoverage c = regionCoverage();
        for (String text : List.of("банкомат рядом", "заправка в 2 км", "спортзал рядом", "нет парковки рядом")) {
            String filter = TextRecipes.parse(text).orElseThrow().filter();
            assertThat(c.missing(filter)).as(text).isPresent();
        }
        for (String text : List.of("аптека в 5 минутах", "нет ночных клубов рядом", "много кафе рядом", "музей рядом")) {
            String filter = TextRecipes.parse(text).orElseThrow().filter();
            assertThat(c.missing(filter)).as(text).isEmpty();
        }
    }

    @Test
    void approvedSourceExtendsCoverage() throws IOException {
        DataCoverage c = regionCoverage().with("leisure=playground");
        assertThat(c.missing("leisure=playground")).isEmpty();
        assertThat(c.missing("leisure=park|playground")).isEmpty();
        assertThat(DataCoverage.of(List.of("shop=*")).missing("shop=bakery|supermarket")).isEmpty();
    }

    @Test
    void queryIsBuiltOnlyFromCheckedFilterAndRegionBounds() {
        String q = OverpassQuery.build("amenity=atm|bank;leisure=playground", 44.38, 33.95, 44.72, 34.46);
        assertThat(q).startsWith("[out:json]")
                .contains("nwr[\"amenity\"~\"^(atm|bank)$\"](44.3800,33.9500,44.7200,34.4600);")
                .contains("nwr[\"leisure\"=\"playground\"](44.3800,33.9500,44.7200,34.4600);")
                .endsWith("out tags geom;");
    }

    @Test
    void filterRejectsAnythingThatCouldBreakTheQuery() {
        assertThatThrownBy(() -> OsmFilter.parse("amenity=pharmacy\"];out;")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OsmFilter.parse("amenity=a b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OsmFilter.parse("Amenity=pharmacy")).isInstanceOf(IllegalArgumentException.class);
        assertThat(OsmFilter.parse("amenity=fast_food|cafe").rules()).containsKey("amenity");
    }
}
