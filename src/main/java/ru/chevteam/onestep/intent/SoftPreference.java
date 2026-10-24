package ru.chevteam.onestep.intent;

/** «Мягкие» пожелания — оцениваются по агрегированным оценкам гостей, а не по факту наличия. */
public enum SoftPreference {
    QUIET("Тишина"),
    CLEAN("Чистота"),
    GOOD_WIFI("Wi‑Fi");

    private final String title;

    SoftPreference(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
