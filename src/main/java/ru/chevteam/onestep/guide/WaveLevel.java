package ru.chevteam.onestep.guide;

/** Волнение моря. */
public enum WaveLevel {
    CALM("Спокойное море"),
    MODERATE("Небольшие волны"),
    HIGH("Сильные волны");

    private final String title;

    WaveLevel(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
