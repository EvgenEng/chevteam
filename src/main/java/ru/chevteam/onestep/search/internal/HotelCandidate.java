package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.NearbyPlace;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-model кандидата: отель + самый дешёвый подходящий тариф + гео-контекст.
 * Nullable-поля — данных по аспекту нет.
 */
public record HotelCandidate(
        UUID id,
        String name,
        String country,
        String city,
        String district,
        int stars,
        BigDecimal rating,
        int reviewsCount,
        GeoPoint location,
        Integer beachLine,
        Integer distanceToBeachM,
        Integer distanceToCenterM,
        Integer airportTransferMin,
        BigDecimal quietScore,
        BigDecimal cleanScore,
        BigDecimal wifiScore,
        BigDecimal familyScore,
        String description,
        Set<Amenity> amenities,
        Rate rate,
        BigDecimal seasonCoefficient,
        Integer landmarkDistanceM,
        List<NearbyPlace> nearby) {

    public HotelCandidate {
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
        nearby = nearby == null ? List.of() : List.copyOf(nearby);
    }

    public record Rate(String roomName, MealPlan mealPlan, BigDecimal pricePerNight, boolean refundable) {
    }
}
