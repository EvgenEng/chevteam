package ru.chevteam.onestep.guide;

/** Покрытие пляжа. */
public enum BeachSurface {
    WHITE_SAND("Белый песок", true),
    GOLDEN_SAND("Золотистый песок", true),
    DARK_SAND("Тёмный вулканический песок", true),
    CORAL_SAND("Коралловый песок", true),
    SHELL_SAND("Песок с ракушкой", true),
    MIXED("Песок с мелкой галькой", false),
    FINE_PEBBLE("Мелкая галька", false),
    COARSE_PEBBLE("Крупная галька", false),
    ROCKY("Камни и плиты", false);

    private final String title;
    private final boolean sandy;

    BeachSurface(String title, boolean sandy) {
        this.title = title;
        this.sandy = sandy;
    }

    public String title() {
        return title;
    }

    public boolean sandy() {
        return sandy;
    }

    /** Галька, камни: нужны тапочки, а коляске и тем, кто плохо ходит, сложно. */
    public boolean rough() {
        return this == COARSE_PEBBLE || this == ROCKY;
    }
}
