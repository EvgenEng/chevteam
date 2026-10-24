package ru.chevteam.onestep.search.internal;

import java.util.Locale;

final class Distance {

    private Distance() {
    }

    static String format(int meters) {
        if (meters < 1000) {
            return meters + " м";
        }
        return String.format(Locale.ROOT, "%.1f км", meters / 1000.0).replace('.', ',');
    }
}
