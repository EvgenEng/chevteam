package ru.chevteam.onestep.guide;

/** Ощущение погоды: «изнуряющая жара», «комфортно», «сезон дождей». Выводится из температуры и осадков. */
public enum Comfort {
    COLD("Холодно"),
    COOL("Свежо"),
    MILD("Прохладно"),
    COMFORT("Комфортно"),
    RAINY("Тепло, но дождливо"),
    HOT("Жарко"),
    EXHAUSTING("Изнуряющая жара");

    private final String title;

    Comfort(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /**
     * @param airC средняя дневная температура воздуха
     */
    public static Comfort of(int airC, RainLevel rain) {
        if (airC >= 35) {
            return EXHAUSTING;
        }
        if (rain == RainLevel.HEAVY && airC >= 18) {
            return RAINY;
        }
        if (airC >= 31) {
            return HOT;
        }
        if (airC >= 22) {
            return COMFORT;
        }
        if (airC >= 15) {
            return MILD;
        }
        return airC >= 5 ? COOL : COLD;
    }
}
