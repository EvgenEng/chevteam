package ru.chevteam.onestep.enrichment.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.tools.BuiltIns;
import ru.chevteam.onestep.enrichment.internal.tools.Characteristic;
import ru.chevteam.onestep.enrichment.internal.tools.Computed;
import ru.chevteam.onestep.enrichment.internal.tools.Site;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия на настоящем снимке OpenStreetMap: известные места Ялты должны получать ожидаемые выводы.
 * Если снимок обновят и выводы поменяются — тест покажет, что именно изменилось.
 */
class YaltaSnapshotTest {

    static OsmSnapshot osm;

    @BeforeAll
    static void load() {
        osm = new OsmSnapshotLoader(new ObjectMapper(), new EnrichmentProperties(false, null, null, null, null, null, null))
                .load().snapshot();
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
