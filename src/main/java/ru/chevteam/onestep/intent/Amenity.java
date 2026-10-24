package ru.chevteam.onestep.intent;

/** Удобства и инфраструктура отеля — проверяются по факту наличия. */
public enum Amenity {
    AQUAPARK("Аквапарк"),
    POOL("Бассейн"),
    KIDS_CLUB("Детский клуб"),
    SPA("СПА"),
    PARKING("Парковка"),
    GYM("Фитнес-зал"),
    RESTAURANT("Ресторан в отеле"),
    BAR("Бар"),
    MOTO_PARKING("Парковка для мотоцикла"),
    EV_CHARGING("Зарядка для электромобиля"),
    BIKE_RENTAL("Прокат велосипедов"),
    MOTO_RENTAL("Прокат скутеров и байков"),
    CAR_RENTAL("Прокат авто");

    private final String title;

    Amenity(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
