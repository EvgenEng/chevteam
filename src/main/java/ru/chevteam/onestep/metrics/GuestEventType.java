package ru.chevteam.onestep.metrics;

/** Шаги гостя, по которым считаем время до выбора. */
public enum GuestEventType {
    /** Гость отправил запрос и получил выдачу. */
    SEARCH,
    /** Открыл сравнение отелей. */
    COMPARE,
    /** Нажал «Забронировать» — выбор сделан. */
    CHOOSE,
    /** Бронь оплачена. */
    BOOKED
}
