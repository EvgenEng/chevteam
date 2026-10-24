package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.MealPlan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HotelMatch(
        UUID hotelId,
        String name,
        String city,
        String district,
        int stars,
        BigDecimal rating,
        int reviewsCount,
        String description,
        GeoPoint location,
        int matchPercent,
        PriceQuote price,
        UUID rateId,
        String roomName,
        MealPlan mealPlan,
        boolean refundable,
        List<MatchReason> reasons,
        List<NearbyPlace> nearby) {
}
