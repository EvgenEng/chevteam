package ru.chevteam.onestep.guide;

/** Сезонные риски, о которых стоит знать заранее. */
public enum HazardKind {
    JELLYFISH("Медузы", true),
    MOSQUITO("Комары и мошка", false),
    TICK("Клещи", false),
    RIP_CURRENT("Волны и течения", true),
    TYPHOON("Тайфуны и штормы", false),
    ALGAE("Цветение воды", true),
    SEA_URCHIN("Морские ежи и кораллы", true),
    HEAT("Жара", false),
    COLD_WATER("Холодная вода", true),
    SMOKE("Дымка от пожаров", false),
    SEAWEED("Водоросли", true),
    DEBRIS("Мусор на пляжах", true),
    CROCODILE("Крокодилы", true),
    SANDFLY("Песчаные мошки", true),
    FROST("Морозы", false);

    private final String title;
    private final boolean beachRelated;

    HazardKind(String title, boolean beachRelated) {
        this.title = title;
        this.beachRelated = beachRelated;
    }

    public String title() {
        return title;
    }

    /** Риск касается моря и пляжа — для городской поездки его можно не показывать. */
    public boolean beachRelated() {
        return beachRelated;
    }
}
