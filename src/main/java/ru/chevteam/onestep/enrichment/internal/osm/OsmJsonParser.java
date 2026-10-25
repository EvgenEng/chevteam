package ru.chevteam.onestep.enrichment.internal.osm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.chevteam.onestep.enrichment.internal.geo.Point;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ответ Overpass API ({@code out tags geom}) → снимок. */
public final class OsmJsonParser {

    private final ObjectMapper mapper;

    public OsmJsonParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public OsmSnapshot parse(InputStream json) throws IOException {
        JsonNode root = mapper.readTree(json);
        Instant takenAt = null;
        JsonNode base = root.path("osm3s").path("timestamp_osm_base");
        if (base.isTextual()) {
            try {
                takenAt = Instant.parse(base.asText());
            } catch (DateTimeParseException ignored) {
                // дата выгрузки не обязательна
            }
        }
        List<OsmElement> elements = new ArrayList<>();
        for (JsonNode e : root.path("elements")) {
            Map<String, String> tags = new HashMap<>();
            e.path("tags").fields().forEachRemaining(f -> tags.put(f.getKey(), f.getValue().asText()));
            List<Point> geometry = new ArrayList<>();
            String type = e.path("type").asText();
            if ("node".equals(type)) {
                geometry.add(new Point(e.path("lat").asDouble(), e.path("lon").asDouble()));
            } else if ("way".equals(type)) {
                for (JsonNode p : e.path("geometry")) {
                    geometry.add(new Point(p.path("lat").asDouble(), p.path("lon").asDouble()));
                }
            } else if ("relation".equals(type)) {
                // мультиполигон (пляж, парк): берём самый длинный внешний контур
                List<Point> best = List.of();
                for (JsonNode m : e.path("members")) {
                    if (!"outer".equals(m.path("role").asText()) && !m.path("role").asText().isEmpty()) {
                        continue;
                    }
                    List<Point> ring = new ArrayList<>();
                    for (JsonNode p : m.path("geometry")) {
                        ring.add(new Point(p.path("lat").asDouble(), p.path("lon").asDouble()));
                    }
                    if (ring.size() > best.size()) {
                        best = ring;
                    }
                }
                geometry.addAll(best);
                if (geometry.isEmpty() && e.has("bounds")) {
                    JsonNode b = e.path("bounds");
                    geometry.add(new Point((b.path("minlat").asDouble() + b.path("maxlat").asDouble()) / 2,
                            (b.path("minlon").asDouble() + b.path("maxlon").asDouble()) / 2));
                }
            }
            if (!geometry.isEmpty()) {
                elements.add(new OsmElement(type, e.path("id").asLong(), tags, geometry));
            }
        }
        return new OsmSnapshot(elements, takenAt);
    }
}
