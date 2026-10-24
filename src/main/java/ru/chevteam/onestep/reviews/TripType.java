package ru.chevteam.onestep.reviews;

/** С кем ездил автор отзыва — главный фильтр: семье важно, что пишут семьи. */
public enum TripType {
    FAMILY("Семьи с детьми"),
    COUPLE("Пары"),
    SOLO("Соло"),
    FRIENDS("С друзьями"),
    SENIORS("С пожилыми"),
    BUSINESS("По работе");

    private final String title;

    TripType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
