package ru.chevteam.onestep.intent;

public enum LocationFeature {
    FIRST_LINE("Первая линия"),
    CITY_CENTER("Центр города"),
    AWAY_FROM_TRAFFIC("Вдали от оживлённых дорог"),
    CAR_ACCESS("Подъезд на машине"),
    SHOPPING("Шопинг рядом"),
    /** Кафе и рестораны в пешей доступности — где позавтракать и поужинать. */
    FOOD_NEARBY("Кафе рядом");

    private final String title;

    LocationFeature(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
