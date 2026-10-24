package ru.chevteam.onestep.search;

/**
 * Место рядом с отелем.
 *
 * @param kind            тип места: SIGHT, MUSEUM, PARK, NATURE, VIEWPOINT, PROMENADE, FOOD, SHOPPING
 * @param walkMinutes     обычный темп
 * @param slowWalkMinutes спокойный темп — для пожилых, с коляской, для тех, кому трудно ходить
 * @param note            подсказка: «по надземному переходу от метро», «есть IKEA»
 */
public record NearbyPlace(String name, String kind, int distanceMeters, int walkMinutes, int slowWalkMinutes, String note) {

    /** Дальше этого предлагаем такси. */
    public static final int MAX_COMFORT_WALK_MINUTES = 20;

    public boolean walkable() {
        return walkMinutes <= MAX_COMFORT_WALK_MINUTES;
    }
}
