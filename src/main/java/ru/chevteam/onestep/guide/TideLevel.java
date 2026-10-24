package ru.chevteam.onestep.guide;

/** Сила приливов и отливов. */
public enum TideLevel {
    NONE("Отливы почти незаметны"),
    MODERATE("Умеренные отливы"),
    STRONG("Сильные отливы");

    private final String title;

    TideLevel(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
