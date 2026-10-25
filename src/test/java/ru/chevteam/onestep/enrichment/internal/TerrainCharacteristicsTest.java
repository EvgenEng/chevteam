package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.internal.geo.DemGrid;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.tools.Characteristic;
import ru.chevteam.onestep.enrichment.internal.tools.Computed;
import ru.chevteam.onestep.enrichment.internal.tools.Site;
import ru.chevteam.onestep.enrichment.internal.tools.TerrainCharacteristics;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Синтетический берег: море на юге (широта < 44.495), суша поднимается на север на 1 м каждые 10 м.
 * Отель A — у самого берега, B — на склоне в 600 м от моря, C — за хребтом, который закрывает море.
 */
class TerrainCharacteristicsTest {

    static final double S = 44.48;
    static final double N = 44.52;
    static final double W = 34.15;
    static final double E = 34.20;
    static final double SHORE = 44.495;
    static final int ROWS = 401;
    static final int COLS = 401;

    static DemGrid dem(boolean ridge) {
        short[] h = new short[ROWS * COLS];
        for (int r = 0; r < ROWS; r++) {
            double lat = N - (N - S) * r / (ROWS - 1);
            for (int c = 0; c < COLS; c++) {
                double metersFromShore = (lat - SHORE) * 111_195;
                double height = metersFromShore <= 0 ? 0 : metersFromShore / 10;
                if (ridge && metersFromShore > 300 && metersFromShore < 400) {
                    height += 120; // хребет между морем и отелем C
                }
                h[r * COLS + c] = (short) Math.round(height);
            }
        }
        return new DemGrid(S, W, N, E, ROWS, COLS, h);
    }

    static Point north(double meters) {
        return new Point(SHORE + meters / 111_195, 34.175);
    }

    static final OsmSnapshot COAST = new OsmSnapshot(List.of(new OsmElement("way", 1, Map.of("natural", "coastline"),
            List.of(new Point(SHORE, 34.16), new Point(SHORE, 34.19)))), null);

    static Computed compute(DemGrid dem, String code, Point at) {
        Characteristic c = TerrainCharacteristics.all(dem).stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
        return c.compute(new Site(UUID.randomUUID(), "t", at), COAST).orElseThrow();
    }

    @Test
    void elevationIsInterpolated() {
        DemGrid d = dem(false);
        assertThat(d.elevation(north(500))).isCloseTo(50, within(1.5));
        assertThat(d.elevation(new Point(44.49, 34.175))).isZero();
        assertThat(d.elevation(new Point(45.0, 34.175))).isNaN();
    }

    @Test
    void climbFromTheSea() {
        Computed nearShore = compute(dem(false), TerrainCharacteristics.CLIMB_TO_SEA, north(40));
        assertThat(nearShore.level()).isEqualTo("FLAT");
        Computed onSlope = compute(dem(false), TerrainCharacteristics.CLIMB_TO_SEA, north(600));
        assertThat(onSlope.level()).isEqualTo("STEEP");
        assertThat(onSlope.number()).isCloseTo(60, within(3.0));
        assertThat(onSlope.value()).contains("в гору");
    }

    @Test
    void seaViewDependsOnTerrainInBetween() {
        Computed open = compute(dem(false), TerrainCharacteristics.SEA_VIEW, north(600));
        assertThat(open.level()).as(open.toString()).isEqualTo("YES");
        assertThat(compute(dem(true), TerrainCharacteristics.SEA_VIEW, north(600)).level()).isEqualTo("NO");
        // море тянется и на юго-запад — зимой солнце садится над водой
        assertThat(compute(dem(false), TerrainCharacteristics.SUNSET_VIEW, north(600)).level()).isEqualTo("YES");
        // за хребтом заката над морем не видно
        assertThat(compute(dem(true), TerrainCharacteristics.SUNSET_VIEW, north(600)).level()).isEqualTo("NO");
    }
}
