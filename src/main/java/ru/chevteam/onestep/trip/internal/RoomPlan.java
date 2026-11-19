package ru.chevteam.onestep.trip.internal;

import ru.chevteam.onestep.catalog.HotelDetails.RoomRate;

/**
 * Сколько номеров тарифа нужно, чтобы разместить всех: взрослые — только на основных местах,
 * дети — на любых свободных (основных или детских).
 */
final class RoomPlan {

    private RoomPlan() {
    }

    /** 0 — тариф не подходит совсем (например, номер без мест для взрослых). */
    static int roomsFor(RoomRate rate, int adults, int children) {
        int perRoomAdults = rate.maxAdults();
        int perRoomAll = rate.maxAdults() + Math.max(0, rate.maxChildren());
        if (perRoomAdults < 1) {
            return 0;
        }
        int byAdults = (adults + perRoomAdults - 1) / perRoomAdults;
        int byAll = (adults + children + perRoomAll - 1) / perRoomAll;
        int rooms = Math.max(byAdults, byAll);
        return rooms > 6 ? 0 : rooms;
    }
}
