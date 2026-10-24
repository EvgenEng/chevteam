package ru.chevteam.onestep.intent;

import java.util.Objects;

/**
 * Направление поездки: страна → регион (необязательно) → город (необязательно).
 * <p>
 * Регион нужен там, где люди думают регионами, а не городами: «Крым», «Карелия», «Золотое кольцо».
 * Если указан город, поиск идёт по городу, а регион — справочная информация.
 */
public record Destination(String country, String region, String city) {

    public Destination {
        Objects.requireNonNull(country, "country");
    }

    public static Destination country(String country) {
        return new Destination(country, null, null);
    }

    public static Destination region(String country, String region) {
        return new Destination(country, region, null);
    }

    public static Destination city(String country, String city) {
        return new Destination(country, null, city);
    }

    public static Destination city(String country, String region, String city) {
        return new Destination(country, region, city);
    }

    public String displayName() {
        return city != null ? city : region != null ? region : country;
    }
}
