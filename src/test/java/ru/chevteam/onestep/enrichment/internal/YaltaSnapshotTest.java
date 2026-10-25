package ru.chevteam.onestep.enrichment.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.geo.DemGrid;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.tools.BuiltIns;
import ru.chevteam.onestep.enrichment.internal.tools.Characteristic;
import ru.chevteam.onestep.enrichment.internal.tools.Computed;
import ru.chevteam.onestep.enrichment.internal.tools.Site;
import ru.chevteam.onestep.enrichment.internal.tools.TerrainCharacteristics;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия на настоящем снимке OpenStreetMap: известные места Ялты должны получать ожидаемые выводы.
 * Если снимок обновят и выводы поменяются — тест покажет, что именно изменилось.
 */
class YaltaSnapshotTest {

    static OsmSnapshot osm;
    static DemGrid dem;

    @BeforeAll
    static void load() {
        OsmSnapshotLoader loader = new OsmSnapshotLoader(new ObjectMapper(),
                new EnrichmentProperties(false, null, null, null, null, null, null, null));
        osm = loader.load().snapshot();
        dem = loader.loadDem();
    }

    static Computed terrain(Point p, String code) {
        return TerrainCharacteristics.all(dem).stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow()
                .compute(new Site(UUID.randomUUID(), "t", p), osm).orElseThrow();
    }

    @Test
    void elevationModelMatchesKnownHeights() {
        assertThat(dem).isNotNull();
        // Ай-Петри — около 1230 м; набережная Ялты — у уровня моря
        assertThat(dem.elevation(new Point(44.4515, 34.0590))).isBetween(1050.0, 1300.0);
        assertThat(dem.elevation(new Point(44.4950, 34.1690))).isLessThan(20.0);
    }

    @Test
    void premiumParkOnTheHillIsAClimbAndSeesTheSea() {
        Point premium = new Point(44.5079, 34.1910);
        assertThat(terrain(premium, TerrainCharacteristics.CLIMB_TO_SEA).level()).isEqualTo("STEEP");
        assertThat(terrain(premium, TerrainCharacteristics.SEA_VIEW).level()).isEqualTo("YES");
        // Ялта смотрит на юго-восток: солнце садится за горы, а не в море
        assertThat(terrain(premium, TerrainCharacteristics.SUNSET_VIEW).level()).isEqualTo("NO");
    }

    static Computed compute(Point p, String code) {
        Characteristic c = BuiltIns.all().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
        return c.compute(new Site(UUID.randomUUID(), "t", p), osm).orElseThrow();
    }

    @Test
    void snapshotIsLoaded() {
        assertThat(osm.elements()).hasSizeGreaterThan(5000);
        assertThat(osm.takenAt()).isNotNull();
    }

    @Test
    void bristolOnRooseveltStreetIsFirstLineButNoisy() {
        Point bristol = new Point(44.49631, 34.17169);
        assertThat(compute(bristol, BuiltIns.SEA_DISTANCE).level()).isEqualTo("FIRST_LINE");
        assertThat(compute(bristol, BuiltIns.ROAD_NOISE).level()).isEqualTo("HIGH");
        assertThat(compute(bristol, BuiltIns.ROAD_NOISE).evidence()).contains("Рузвельта");
    }

    @Test
    void inturistOnTheHillHasStairsToTheSea() {
        Point inturist = new Point(44.50290, 34.19068);
        assertThat(compute(inturist, BuiltIns.STAIRS_TO_SEA).level()).isEqualTo("MANY");
    }
}
