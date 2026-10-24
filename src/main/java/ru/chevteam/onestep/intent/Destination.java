package ru.chevteam.onestep.intent;

import java.util.Objects;

/**
 * Направление поездки. {@code city} может отсутствовать — тогда ищем по всей стране.
 */
public record Destination(String country, String city) {

    public Destination {
        Objects.requireNonNull(country, "country");
    }

    public static Destination country(String country) {
        return new Destination(country, null);
    }

    public static Destination city(String country, String city) {
        return new Destination(country, city);
    }

    public String displayName() {
        return city != null ? city : country;
    }
}
