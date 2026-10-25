package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.geo.Geo;
import ru.chevteam.onestep.enrichment.internal.osm.OsmFilter;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot.Nearby;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Новая характеристика, собранная из готовых инструментов, — «аптека в 5 минутах», «нет ночных клубов рядом».
 * Рецепт хранится как данные (JSON), поэтому характеристику можно добавить без релиза и пересчитать для всех отелей.
 *
 * @param tool      какой инструмент применить
 * @param filter    какие объекты OSM искать: {@code amenity=pharmacy}
 * @param radius    радиус поиска, м
 * @param min       сколько объектов нужно для «да» (для {@link Tool#COUNT})
 * @param noun      как назвать объект в объяснении: «аптека»
 */
public record Recipe(String code, String title, Tool tool, String filter, int radius, int min, String noun) {

    public enum Tool {
        /** Есть ли ближайший объект в радиусе — и как далеко. */
        NEAREST,
        /** Сколько объектов в радиусе; «да» — если не меньше {@code min}. */
        COUNT,
        /** «Нет … рядом»: «да» — если в радиусе ничего нет. */
        ABSENT
    }

    public Recipe {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(tool, "tool");
        OsmFilter.parse(filter); // проверяем сразу: рецепт с ошибкой не сохраняем
        if (radius < 20 || radius > 5000) {
            throw new IllegalArgumentException("Радиус должен быть от 20 м до 5 км: " + radius);
        }
        min = Math.max(1, min);
        noun = noun == null || noun.isBlank() ? "объект" : noun.strip();
    }

    public Characteristic characteristic() {
        OsmFilter f = OsmFilter.parse(filter);
        String method = switch (tool) {
            case NEAREST -> "ближайший объект OSM «" + filter + "» в радиусе " + Format.meters(radius);
            case COUNT -> "объекты OSM «" + filter + "» в радиусе " + Format.meters(radius) + ", нужно не меньше " + min;
            case ABSENT -> "отсутствие объектов OSM «" + filter + "» в радиусе " + Format.meters(radius);
        };
        return new Characteristic() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String title() {
                return title;
            }

            @Override
            public Optional<Computed> compute(Site site, OsmSnapshot osm) {
                List<Nearby> found = osm.within(site.location(), radius, f);
                return Optional.of(switch (tool) {
                    case NEAREST -> found.isEmpty()
                            ? new Computed("нет в " + Format.meters(radius), "NO", null, 0.6, method, "—")
                            : new Computed(Format.meters(found.get(0).meters()) + " · "
                                    + Format.minutes(Geo.walkMinutes(found.get(0).meters())), "YES",
                                    Math.rint(found.get(0).meters()), 0.75, method, BuiltIns.names(found, noun, 2));
                    case COUNT -> new Computed(found.size() + " в " + Format.meters(radius), found.size() >= min ? "YES" : "NO",
                            (double) found.size(), 0.7, method, BuiltIns.names(found, noun, 3));
                    case ABSENT -> found.isEmpty()
                            ? new Computed("нет в " + Format.meters(radius), "YES", 0.0, 0.6, method, "—")
                            : new Computed(BuiltIns.names(found, noun, 1), "NO", Math.rint(found.get(0).meters()), 0.75,
                                    method, BuiltIns.names(found, noun, 3));
                });
            }
        };
    }
}
