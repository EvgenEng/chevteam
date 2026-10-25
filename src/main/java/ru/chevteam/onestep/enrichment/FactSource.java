package ru.chevteam.onestep.enrichment;

/** Откуда известен факт. */
public enum FactSource {
    /** Вычислено по OpenStreetMap. */
    OSM("карта OpenStreetMap"),
    /** Посчитано по текстам отзывов гостей. */
    REVIEWS("отзывы гостей"),
    /** Заявлено отельером в экстранете. */
    HOTELIER("данные отеля");

    private final String title;

    FactSource(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
