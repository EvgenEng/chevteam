package ru.chevteam.onestep.intent;

public enum LocationFeature {
    FIRST_LINE("Первая линия"),
    CITY_CENTER("Центр города");

    private final String title;

    LocationFeature(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
