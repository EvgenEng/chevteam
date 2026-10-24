package ru.chevteam.onestep.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HotelDetails(
        UUID id,
        String name,
        String country,
        String city,
        String district,
        String address,
        int stars,
        BigDecimal rating,
        int reviewsCount,
        String description,
        double lat,
        double lon,
        List<String> amenities,
        GuestScores scores,
        List<RoomRate> rates) {

    public record GuestScores(BigDecimal quiet, BigDecimal clean, BigDecimal wifi, BigDecimal family) {
    }

    public record RoomRate(UUID id, String roomName, String mealPlan, int maxAdults, int maxChildren,
                           BigDecimal basePricePerNight, boolean refundable) {
    }
}
