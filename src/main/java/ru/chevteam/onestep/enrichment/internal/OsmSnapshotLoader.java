package ru.chevteam.onestep.enrichment.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmJsonParser;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Читает снимок OpenStreetMap, выгруженный скриптом {@code tools/osm/fetch-yalta-alushta.sh}.
 * Снимок — входные данные пайплайна, а не готовые характеристики: всё остальное считается кодом.
 */
@Component
class OsmSnapshotLoader {

    private final OsmJsonParser parser;
    private final EnrichmentProperties props;

    OsmSnapshotLoader(ObjectMapper mapper, EnrichmentProperties props) {
        this.parser = new OsmJsonParser(mapper);
        this.props = props;
    }

    Loaded load() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(props.snapshot());
            Arrays.sort(files, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            List<OsmElement> all = new ArrayList<>();
            Instant takenAt = null;
            List<String> names = new ArrayList<>();
            for (Resource r : files) {
                try (InputStream in = r.getInputStream()) {
                    OsmSnapshot part = parser.parse(in);
                    all.addAll(part.elements());
                    if (part.takenAt() != null && (takenAt == null || part.takenAt().isBefore(takenAt))) {
                        takenAt = part.takenAt();
                    }
                    names.add(r.getFilename());
                }
            }
            return new Loaded(new OsmSnapshot(all, takenAt), "OpenStreetMap: " + String.join(", ", names));
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать снимок OSM " + props.snapshot(), e);
        }
    }

    /** Карта высот региона или {@code null}, если файла нет. */
    ru.chevteam.onestep.enrichment.internal.geo.DemGrid loadDem() {
        Resource r = new PathMatchingResourcePatternResolver().getResource(props.dem());
        if (!r.exists()) {
            return null;
        }
        try (InputStream in = r.getInputStream()) {
            return ru.chevteam.onestep.enrichment.internal.geo.DemGrid.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать карту высот " + props.dem(), e);
        }
    }

    record Loaded(OsmSnapshot snapshot, String description) {
    }
}
