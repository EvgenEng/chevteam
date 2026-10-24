package ru.chevteam.onestep.guide;

/** Насколько вероятна встреча в сезон. */
public enum Chance {
    LOW("изредка"),
    MEDIUM("часто"),
    HIGH("почти всегда");

    private final String title;

    Chance(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
