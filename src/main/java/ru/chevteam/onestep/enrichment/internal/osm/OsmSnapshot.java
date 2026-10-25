package ru.chevteam.onestep.enrichment.internal.osm;

import ru.chevteam.onestep.enrichment.internal.geo.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Снимок OpenStreetMap для региона с сеточным индексом (~1 км): поиск «что в радиусе 300 м»
 * перебирает только соседние ячейки, а не все десятки тысяч объектов.
 *
 * @param takenAt когда данные выгружены из OSM (из поля {@code osm3s.timestamp_osm_base})
 */
public final class OsmSnapshot {

    private static final double CELL_DEG = 0.01;

    private final List<OsmElement> elements;
    private final Instant takenAt;
    private final Map<Long, List<OsmElement>> grid = new HashMap<>();

    public OsmSnapshot(List<OsmElement> elements, Instant takenAt) {
        this.elements = List.copyOf(elements);
        this.takenAt = takenAt;
        for (OsmElement e : this.elements) {
            Set<Long> cells = new LinkedHashSet<>();
            for (Point p : e.geometry()) {
                cells.add(cell(p.lat(), p.lon()));
            }
            for (long c : cells) {
                grid.computeIfAbsent(c, k -> new ArrayList<>()).add(e);
            }
        }
    }

    public List<OsmElement> elements() {
        return elements;
    }

    public Instant takenAt() {
        return takenAt;
    }

    /** Объекты, у которых хоть одна вершина в квадрате радиуса вокруг точки, ближайшие первыми. */
    public List<Nearby> within(Point center, double radiusMeters, Predicate<OsmElement> filter) {
        double dLat = radiusMeters / 111_320.0;
        double dLon = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(center.lat())));
        // длинные объекты (береговая линия, трасса) могут не иметь вершин рядом — расширяем поиск на ячейку
        int r = (int) Math.ceil(Math.max(dLat, dLon) / CELL_DEG) + 1;
        long cy = Math.round(Math.floor(center.lat() / CELL_DEG));
        long cx = Math.round(Math.floor(center.lon() / CELL_DEG));
        Set<OsmElement> seen = new LinkedHashSet<>();
        for (long y = cy - r; y <= cy + r; y++) {
            for (long x = cx - r; x <= cx + r; x++) {
                List<OsmElement> list = grid.get(key(y, x));
                if (list != null) {
                    for (OsmElement e : list) {
                        if (filter.test(e)) {
                            seen.add(e);
                        }
                    }
                }
            }
        }
        List<Nearby> result = new ArrayList<>();
        for (OsmElement e : seen) {
            double d = e.metersFrom(center);
            if (d <= radiusMeters) {
                result.add(new Nearby(e, d));
            }
        }
        result.sort(Comparator.comparingDouble(Nearby::meters));
        return result;
    }

    public Optional<Nearby> nearest(Point center, double maxMeters, Predicate<OsmElement> filter) {
        List<Nearby> list = within(center, maxMeters, filter);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private static long cell(double lat, double lon) {
        return key(Math.round(Math.floor(lat / CELL_DEG)), Math.round(Math.floor(lon / CELL_DEG)));
    }

    private static long key(long y, long x) {
        return y * 1_000_003L + x;
    }

    /** Объект и расстояние до него от отеля. */
    public record Nearby(OsmElement element, double meters) {
    }
}
