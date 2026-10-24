package ru.chevteam.onestep.search.internal;

import java.util.Locale;

final class Distance {

    private Distance() {
    }

    /** Расстояние по прямой (гаверсинус), метры. */
    static int meters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return (int) Math.round(2 * 6_371_008.8 * Math.asin(Math.sqrt(a)));
    }

    static String format(int meters) {
        if (meters < 1000) {
            return meters + " м";
        }
        if (meters >= 10_000) {
            return Math.round(meters / 1000.0) + " км";
        }
        return String.format(Locale.ROOT, "%.1f км", meters / 1000.0).replace('.', ',');
    }
}
