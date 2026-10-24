package ru.chevteam.onestep.guide;

/** Режим въезда для граждан РФ — от «по внутреннему паспорту» до «нужна виза». */
public enum EntryRegime {
    DOMESTIC("Поездка по России"),
    VISA_FREE("Без визы"),
    PRE_AUTHORIZATION("Без визы, но нужно электронное разрешение"),
    VISA_ON_ARRIVAL("Виза по прилёте"),
    E_VISA("Электронная виза"),
    VISA_REQUIRED("Нужна виза");

    private final String title;

    EntryRegime(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /** Можно лететь, ничего не оформляя заранее. */
    public boolean noPaperwork() {
        return this == DOMESTIC || this == VISA_FREE || this == VISA_ON_ARRIVAL;
    }
}
