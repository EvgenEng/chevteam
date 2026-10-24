package ru.chevteam.onestep.guide;

/** Как можно увидеть животных: бесплатно с берега или в море за отдельную плату. */
public enum ViewingMode {
    SHORE("с берега"),
    BOAT("с лодки или яхты"),
    SNORKEL("при снорклинге и дайвинге");

    private final String title;

    ViewingMode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
