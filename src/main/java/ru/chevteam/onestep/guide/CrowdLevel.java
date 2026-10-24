package ru.chevteam.onestep.guide;

/** Загруженность пляжа в высокий сезон. */
public enum CrowdLevel {
    LOW("Немноголюдно"),
    MEDIUM("Умеренно людно"),
    HIGH("Многолюдно в сезон");

    private final String title;

    CrowdLevel(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
