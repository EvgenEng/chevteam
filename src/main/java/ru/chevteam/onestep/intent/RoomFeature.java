package ru.chevteam.onestep.intent;

/** Удобства номера и услуги, о которых важно знать заранее — и их наличие, и отсутствие. */
public enum RoomFeature {
    WIFI_ROOM("Wi‑Fi в номере"),
    BABY_COT("Детская кроватка"),
    EXTRA_BED("Дополнительная кровать"),
    AIR_CON("Кондиционер"),
    KITCHEN("Кухня"),
    BALCONY("Балкон"),
    FRIDGE("Холодильник"),
    KETTLE("Чайник"),
    LAUNDRY("Стирка"),
    AIRPORT_SHUTTLE("Трансфер из аэропорта"),
    RECEPTION_24H("Круглосуточная стойка");

    private final String title;

    RoomFeature(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
