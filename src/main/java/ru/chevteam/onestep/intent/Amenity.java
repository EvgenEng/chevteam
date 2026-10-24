package ru.chevteam.onestep.intent;

/** Удобства отеля — проверяются по факту наличия. */
public enum Amenity {
    AQUAPARK("Аквапарк"),
    POOL("Бассейн"),
    KIDS_CLUB("Детский клуб"),
    SPA("СПА"),
    PARKING("Парковка"),
    GYM("Фитнес-зал");

    private final String title;

    Amenity(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
