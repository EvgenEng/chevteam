package ru.chevteam.onestep.guide;

/** Вход в воду. */
public enum BeachEntry {
    GENTLE("Пологий вход"),
    MODERATE("Умеренный уклон"),
    STEEP("Крутой вход, глубоко у берега"),
    PONTOON("Вход с понтона");

    private final String title;

    BeachEntry(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
