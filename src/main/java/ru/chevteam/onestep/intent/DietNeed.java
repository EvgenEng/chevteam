package ru.chevteam.onestep.intent;

/** Ограничения и предпочтения в еде: проверяются по кухне отеля и кафе рядом. */
public enum DietNeed {
    VEGETARIAN("Вегетарианское меню"),
    VEGAN("Веганское меню"),
    HALAL("Халяль"),
    KOSHER("Кошерное питание"),
    /** Индийская вегетарианская кухня без говядины — важно для индуистов. */
    HINDU("Индийская вегетарианская кухня"),
    GLUTEN_FREE("Без глютена"),
    DIETARY("Диетическое меню"),
    KIDS_MENU("Детское меню");

    private final String title;

    DietNeed(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /** Веганское меню подходит и вегетарианцу. */
    public boolean satisfiedBy(DietNeed offered) {
        return offered == this || this == VEGETARIAN && (offered == VEGAN || offered == HINDU);
    }
}
