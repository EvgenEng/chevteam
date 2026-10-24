package ru.chevteam.onestep.search.internal;

/**
 * Перевод расстояния «по прямой» во время пешком.
 * 80 м/мин (~4.8 км/ч) и коэффициент извилистости городских маршрутов 1.25.
 * Спокойный темп — 50 м/мин (~3 км/ч): пожилые, дети, коляска, трость.
 */
final class WalkingTime {

    static final double METERS_PER_MINUTE = 80.0;
    static final double SLOW_METERS_PER_MINUTE = 50.0;
    static final double DETOUR_FACTOR = 1.25;

    private WalkingTime() {
    }

    static int minutes(int straightLineMeters) {
        return minutes(straightLineMeters, METERS_PER_MINUTE);
    }

    static int slowMinutes(int straightLineMeters) {
        return minutes(straightLineMeters, SLOW_METERS_PER_MINUTE);
    }

    private static int minutes(int straightLineMeters, double pace) {
        return Math.max(1, (int) Math.ceil(straightLineMeters * DETOUR_FACTOR / pace));
    }

    /** Радиус поиска по прямой, при котором время пешком не превысит {@code minutes}. */
    static int radiusMeters(double minutes) {
        return (int) Math.floor(minutes * METERS_PER_MINUTE / DETOUR_FACTOR);
    }
}
