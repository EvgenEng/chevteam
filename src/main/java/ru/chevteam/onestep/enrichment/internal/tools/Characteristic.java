package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;

import java.util.Optional;

/** Характеристика, которую пайплайн умеет вычислить для любого отеля региона. */
public interface Characteristic {

    String code();

    String title();

    /** Пусто — данных для вывода нет (а не «нет такого рядом»: это тоже вывод). */
    Optional<Computed> compute(Site site, OsmSnapshot osm);
}
