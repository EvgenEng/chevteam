package ru.chevteam.onestep.enrichment.internal.osm;

import ru.chevteam.onestep.enrichment.internal.geo.Geo;
import ru.chevteam.onestep.enrichment.internal.geo.Point;

import java.util.List;
import java.util.Map;

/**
 * Объект OpenStreetMap: точка (node), линия или контур (way), отношение (relation, берём внешний контур).
 *
 * @param geometry для node — одна точка; для way — вершины по порядку
 */
public record OsmElement(String type, long id, Map<String, String> tags, List<Point> geometry) {

    public OsmElement {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }

    public String tag(String key) {
        return tags.get(key);
    }

    /** Имя по-русски, затем местное. */
    public String name() {
        String ru = tags.get("name:ru");
        return ru != null ? ru : tags.get("name");
    }

    public boolean closed() {
        return geometry.size() > 3 && geometry.get(0).equals(geometry.get(geometry.size() - 1));
    }

    /** Центр — для отелей и мест, нарисованных контуром. */
    public Point center() {
        if (geometry.size() == 1) {
            return geometry.get(0);
        }
        double lat = 0;
        double lon = 0;
        int n = closed() ? geometry.size() - 1 : geometry.size();
        for (int i = 0; i < n; i++) {
            lat += geometry.get(i).lat();
            lon += geometry.get(i).lon();
        }
        return new Point(lat / n, lon / n);
    }

    /** Расстояние от точки: 0 внутри контура, иначе до ближайшей вершины или ребра. */
    public double metersFrom(Point p) {
        if (geometry.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (closed() && Geo.inside(p, geometry)) {
            return 0;
        }
        return Geo.metersToLine(p, geometry);
    }

    public String ref() {
        return type + "/" + id;
    }
}
