package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.TransitKind;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Всё, что важно знать до бронирования, но обычно ищут по другим сайтам: как добраться,
 * чем кормят, что есть на территории и где поесть рядом.
 *
 * @param transport  аэропорт, вокзал, метро — с расстоянием и временем в пути
 * @param facilities инфраструктура «есть / нет»: бассейн, бар, парковка, прокат
 * @param dining     кухни и ограничения в питании или {@code null}, если своего питания нет
 * @param mealAddons какое питание можно добавить к тарифу и почём
 * @param foodNearby кафе и рестораны в пешей доступности
 */
public record HotelServices(List<TransitPoint> transport, List<Facility> facilities, Dining dining,
                            List<MealAddon> mealAddons, List<FoodPlace> foodNearby) {

    public static final HotelServices EMPTY = new HotelServices(List.of(), List.of(), null, List.of(), List.of());

    public HotelServices {
        transport = transport == null ? List.of() : List.copyOf(transport);
        facilities = facilities == null ? List.of() : List.copyOf(facilities);
        mealAddons = mealAddons == null ? List.of() : List.copyOf(mealAddons);
        foodNearby = foodNearby == null ? List.of() : List.copyOf(foodNearby);
    }

    public Optional<Facility> facility(Amenity amenity) {
        return facilities.stream().filter(f -> f.facility() == amenity).findFirst();
    }

    /** Самый быстрый вариант добраться до точки этого типа. */
    public Optional<TransitPoint> fastest(TransitKind kind) {
        return transport.stream().filter(t -> t.kind() == kind).min(Comparator.comparingInt(TransitPoint::minutes));
    }

    public Optional<MealAddon> addon(MealPlan plan) {
        return mealAddons.stream().filter(a -> a.plan() == plan).findFirst();
    }

    public boolean serves(DietNeed need) {
        return dining != null && dining.serves(need);
    }

    /** Кафе рядом с подходящей кухней, ближайшее первым. */
    public Optional<FoodPlace> foodFor(DietNeed need, int maxWalkMinutes) {
        return foodNearby.stream()
                .filter(f -> f.walkMinutes() <= maxWalkMinutes && f.diets().stream().anyMatch(need::satisfiedBy))
                .min(Comparator.comparingInt(FoodPlace::distanceMeters));
    }

    /**
     * @param minutes обычное время в пути без пробок
     * @param mode    как обычно добираются
     */
    public record TransitPoint(TransitKind kind, String name, int distanceMeters, int minutes, Mode mode, String note) {

        public enum Mode { WALK, TAXI, TRAIN, BUS, BOAT, SHUTTLE }
    }

    public record Facility(Amenity facility, boolean available, String note) {
    }

    public record Dining(List<String> cuisines, Set<DietNeed> diets, String note) {

        public Dining {
            cuisines = cuisines == null ? List.of() : List.copyOf(cuisines);
            diets = diets == null ? Set.of() : Set.copyOf(diets);
        }

        public boolean serves(DietNeed need) {
            return diets.stream().anyMatch(need::satisfiedBy);
        }
    }

    /**
     * Питание, которое добавляется к тарифу, — цена за человека в сутки.
     *
     * @param childFreeUnder дети младше этого возраста едят бесплатно
     */
    public record MealAddon(MealPlan plan, BigDecimal adultPerNight, BigDecimal childPerNight, int childFreeUnder, String note) {
    }

    /**
     * @param priceLevel 1 — недорого, 2 — средне, 3 — дорого
     */
    public record FoodPlace(String name, int distanceMeters, int walkMinutes, Set<Meal> meals, Set<DietNeed> diets,
                            String cuisine, int priceLevel, String note) {

        public FoodPlace {
            meals = meals == null ? Set.of() : Set.copyOf(meals);
            diets = diets == null ? Set.of() : Set.copyOf(diets);
        }

        public enum Meal { BREAKFAST, LUNCH, DINNER }
    }
}
