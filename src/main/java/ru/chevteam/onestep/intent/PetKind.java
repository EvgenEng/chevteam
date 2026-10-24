package ru.chevteam.onestep.intent;

/** С кем едут: от вида животного зависит, пустит ли отель и на каких условиях. */
public enum PetKind {
    DOG("Собака", "с собакой"),
    CAT("Кошка", "с кошкой"),
    SMALL_PET("Хорёк, крыса, хомяк", "с мелким питомцем"),
    BIRD("Птица", "с птицей"),
    REPTILE("Черепаха, рептилия", "с черепахой или рептилией"),
    /** Человек сказал «с питомцем», но не уточнил, с кем именно. */
    OTHER("Питомец", "с питомцем");

    private final String title;
    private final String with;

    PetKind(String title, String with) {
        this.title = title;
        this.with = with;
    }

    public String title() {
        return title;
    }

    /** Форма для фразы «можно ...»: «с собакой». */
    public String with() {
        return with;
    }
}
