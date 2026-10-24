package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.search.AccessibilityInfo;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.HotelPolicies;
import ru.chevteam.onestep.search.HotelServices;
import ru.chevteam.onestep.search.NearbyPlace;
import ru.chevteam.onestep.search.Surroundings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-model кандидата: отель + самый дешёвый подходящий тариф + гео-контекст.
 * Nullable-поля — данных по аспекту нет.
 *
 * @param beachId  ближайший пляж своего города в пределах 3 км
 * @param shopping ближайший крупный торговый центр в пределах 3 км или {@code null}
 * @param services как добраться, питание, инфраструктура и кафе рядом
 */
public record HotelCandidate(
        UUID id,
        String name,
        String country,
        String region,
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
        List<NearbyPlace> nearby,
        AccessibilityInfo accessibility,
        UUID beachId,
        Integer beachDistanceM,
        HotelPolicies policies,
        Surroundings surroundings,
        NearbyPlace shopping,
        HotelServices services) {

    public HotelCandidate {
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
        nearby = nearby == null ? List.of() : List.copyOf(nearby);
        services = services == null ? HotelServices.EMPTY : services;
    }

    public record Rate(UUID rateId, String roomName, MealPlan mealPlan, BigDecimal pricePerNight, boolean refundable) {
    }
}
