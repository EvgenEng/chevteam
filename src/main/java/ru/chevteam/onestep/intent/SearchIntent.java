package ru.chevteam.onestep.intent;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Структурированное намерение пользователя — контракт между «пониманием» запроса и поиском.
 * Неизменяемый: коллекции копируются, null-коллекции нормализуются в пустые.
 *
 * @param room         удобства, без которых номер не подходит (детская кроватка, кухня…)
 * @param pets         с какими животными едут
 * @param visaFreeOnly человек ищет направления без визы
 * @param wildlife     кого хочется увидеть: дельфинов, черепах, китов
 * @param diet         ограничения в еде: халяль, кошерное, веганское…
 * @param transit      сколько минут готовы добираться до аэропорта, вокзала, метро
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
        TransportPreference transport,
        Set<BeachPreference> beach,
        Set<BeachActivity> activities,
        Set<AccessNeed> access,
        Set<RoomFeature> room,
        Set<PetKind> pets,
        boolean visaFreeOnly,
        Set<Wildlife> wildlife,
        Set<DietNeed> diet,
        List<TransitConstraint> transit) {

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
        beach = immutableEnumSet(beach, BeachPreference.class);
        activities = immutableEnumSet(activities, BeachActivity.class);
        access = immutableEnumSet(access, AccessNeed.class);
        room = immutableEnumSet(room, RoomFeature.class);
        pets = immutableEnumSet(pets, PetKind.class);
        wildlife = immutableEnumSet(wildlife, Wildlife.class);
        diet = immutableEnumSet(diet, DietNeed.class);
        transit = transit == null ? List.of() : List.copyOf(transit);
    }

    /** Без требований к еде и дороге — для мест, где они не важны (тесты, старые клиенты). */
    public SearchIntent(Destination destination, StayPeriod stay, Guests guests, Budget budget, Integer minStars,
                        MealPlan mealPlan, Set<Amenity> amenities, Set<SoftPreference> softPreferences,
                        Set<LocationFeature> locationFeatures, List<ProximityConstraint> proximity,
                        TransportPreference transport, Set<BeachPreference> beach, Set<BeachActivity> activities,
                        Set<AccessNeed> access, Set<RoomFeature> room, Set<PetKind> pets, boolean visaFreeOnly,
                        Set<Wildlife> wildlife) {
        this(destination, stay, guests, budget, minStars, mealPlan, amenities, softPreferences, locationFeatures, proximity,
                transport, beach, activities, access, room, pets, visaFreeOnly, wildlife, Set.of(), List.of());
    }

    public TravelStyle travelStyle() {
        return TravelStyle.of(guests);
    }

    /** Пляжная поездка: человек что-то сказал про море, пляж или водные активности. */
    public boolean wantsBeach() {
        return !beach.isEmpty() || !activities.isEmpty() || locationFeatures.contains(LocationFeature.FIRST_LINE);
    }

    /** Нужен спокойный темп и минимум ступеней. */
    public boolean needsSlowPace() {
        return access.stream().anyMatch(AccessNeed::slowPace);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> source, Class<E> type) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(source));
    }
}
