package ru.chevteam.onestep.trip;

/** Как гость добирается до места. */
public enum TransportMode {
    PLANE("Самолёт"),
    TRAIN("Поезд"),
    CAR("Автомобиль");

    private final String title;

    TransportMode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
