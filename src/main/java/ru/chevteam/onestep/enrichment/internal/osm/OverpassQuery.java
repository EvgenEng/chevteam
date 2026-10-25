package ru.chevteam.onestep.enrichment.internal.osm;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Запрос к открытому Overpass API (OpenStreetMap) только за нужными объектами и только в границах региона.
 * Собирается из проверенного {@link OsmFilter}, а не из текста пользователя: подставить в запрос что-то своё нельзя.
 */
public final class OverpassQuery {

    private OverpassQuery() {
    }

    public static String build(String filter, double south, double west, double north, double east) {
        String bbox = String.format(Locale.ROOT, "(%.4f,%.4f,%.4f,%.4f)", south, west, north, east);
        StringJoiner body = new StringJoiner("\n", "[out:json][timeout:90];\n(\n", "\n);\nout tags geom;");
        OsmFilter.parse(filter).rules().forEach((key, values) -> {
            String tag = values.isEmpty() ? "[\"" + key + "\"]"
                    : values.size() == 1 ? "[\"" + key + "\"=\"" + values.iterator().next() + "\"]"
                    : "[\"" + key + "\"~\"^(" + String.join("|", values) + ")$\"]";
            body.add("  nwr" + tag + bbox + ";");
        });
        return body.toString();
    }
}
