package ru.chevteam.onestep.intent;

/** Пожелания к пляжу и морю — проверяются по паспорту пляжа и сезонным рискам. */
public enum BeachPreference {
    /** «Хочу на море» — пляж нужен, но без требований к линии. */
    SEA("На море"),
    SANDY("Песчаный пляж"),
    GENTLE_ENTRY("Пологий вход, мелководье"),
    NO_TIDES("Без сильных отливов"),
    NO_JELLYFISH("Без медуз"),
    BEACH_FUN("Развлечения на пляже"),
    UNCROWDED("Немноголюдный пляж");

    private final String title;

    BeachPreference(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
