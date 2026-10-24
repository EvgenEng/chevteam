package ru.chevteam.onestep.intent;

/**
 * Особые потребности путешественников. Влияют на подбор отеля (номер, лифт, ступени),
 * на оценку района (рельеф) и на время пешком до мест рядом.
 */
public enum AccessNeed {
    WHEELCHAIR("На коляске"),
    LIMITED_MOBILITY("Трудно ходить"),
    LOW_VISION("Слабое зрение"),
    SENIORS("С пожилыми");

    private final String title;

    AccessNeed(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /** Нужен ли спокойный темп ходьбы при расчёте «минут пешком». */
    public boolean slowPace() {
        return this != LOW_VISION;
    }
}
