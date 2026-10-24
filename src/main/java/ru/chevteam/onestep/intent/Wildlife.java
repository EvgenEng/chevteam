package ru.chevteam.onestep.intent;

/** Морские обитатели, ради которых едут: у каждого свой сезон и свой способ встречи. */
public enum Wildlife {
    DOLPHINS("Дельфины"),
    SEA_TURTLES("Морские черепахи"),
    WHALES("Киты"),
    MANTA_RAYS("Скаты-манты");

    private final String title;

    Wildlife(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
