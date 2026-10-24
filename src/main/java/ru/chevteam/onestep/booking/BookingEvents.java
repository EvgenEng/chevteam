package ru.chevteam.onestep.booking;

public final class BookingEvents {

    /** Один топик на все события брони — порядок внутри брони гарантирован ключом. */
    public static final String TOPIC = "onestep.bookings";

    private BookingEvents() {
    }
}
