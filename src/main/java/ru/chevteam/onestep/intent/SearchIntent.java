package ru.chevteam.onestep.intent;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Структурированное намерение пользователя — контракт между «пониманием» запроса и поиском.
 * Неизменяемый: коллекции копируются, null-коллекции нормализуются в пустые.
 */
public record SearchIntent(
        Destination destination,
        StayPeriod stay,
        Guests guests,
        Budget budget,
        Integer minStars,
        MealPlan mealPlan,
        Set<Amenity> amenities,
        Set<SoftPreference> softPreferences,
        Set<LocationFeature> locationFeatures,
        List<ProximityConstraint> proximity,
        TransportPreference transport) {

    public SearchIntent {
        Objects.requireNonNull(guests, "guests");
        if (stay == null) {
            stay = new StayPeriod(null, null, null);
        }
        if (minStars != null && (minStars < 1 || minStars > 5)) {
            throw new IllegalArgumentException("minStars must be in [1, 5]: " + minStars);
        }
        amenities = immutableEnumSet(amenities, Amenity.class);
        softPreferences = immutableEnumSet(softPreferences, SoftPreference.class);
        locationFeatures = immutableEnumSet(locationFeatures, LocationFeature.class);
        proximity = proximity == null ? List.of() : List.copyOf(proximity);
    }

    public TravelStyle travelStyle() {
        return TravelStyle.of(guests);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> source, Class<E> type) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(source));
    }
}
