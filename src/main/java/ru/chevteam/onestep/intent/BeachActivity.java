package ru.chevteam.onestep.intent;

/** Водные активности: у каждой свой сезон и свои требования к пляжу. */
public enum BeachActivity {
    SURFING("Серфинг"),
    KITESURFING("Кайтсёрфинг"),
    WINDSURFING("Виндсёрфинг"),
    DIVING("Дайвинг"),
    SNORKELING("Снорклинг");

    private final String title;

    BeachActivity(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
