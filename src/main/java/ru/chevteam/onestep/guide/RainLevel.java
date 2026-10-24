package ru.chevteam.onestep.guide;

/** Осадки в месяц поездки. */
public enum RainLevel {
    DRY("Сухо"),
    LOW("Редкие дожди"),
    MODERATE("Бывают дожди"),
    HEAVY("Сезон дождей");

    private final String title;

    RainLevel(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
