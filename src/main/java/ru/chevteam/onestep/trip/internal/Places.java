package ru.chevteam.onestep.trip.internal;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Справочник мест для демо-модели дороги: откуда гость едет и куда прибывает транспорт у места отдыха.
 * Координаты — центры городов, вокзалов и аэропортов; этого достаточно для оценки по расстоянию.
 */
final class Places {

    record Point(double lat, double lon) {
    }

    /**
     * Город отправления.
     *
     * @param genitive «из Москвы» — для текста
     */
    record City(String name, String genitive, Pattern pattern, Point point, String station, String airport) {
    }

    /**
     * Куда прибывает транспорт у места отдыха.
     *
     * @param planeBlocked почему самолётом нельзя или {@code null}
     * @param trainOk      ходят ли поезда
     * @param carOk        можно ли доехать на машине
     */
    record Arrival(String station, Point stationPoint, String airport, Point airportPoint,
                   String planeBlocked, boolean trainOk, boolean carOk, String note) {
    }

    private static City city(String name, String genitive, String regex, double lat, double lon,
                             String station, String airport) {
        return new City(name, genitive, Pattern.compile("^(?:" + regex + ")$", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
                new Point(lat, lon), station, airport);
    }

    static final List<City> ORIGINS = List.of(
            city("Москва", "Москвы", "москв\\w*|мск", 55.7558, 37.6173, "Москва · вокзал", "Москва · Шереметьево"),
            city("Санкт-Петербург", "Санкт-Петербурга", "санкт[- ]петербург\\w*|петербург\\w*|питер\\w*|спб|с-пб", 59.9343, 30.3351,
                    "Санкт-Петербург · Московский вокзал", "Санкт-Петербург · Пулково"),
            city("Краснодар", "Краснодара", "краснодар\\w*", 45.0355, 38.9753, "Краснодар · вокзал", null),
            city("Ростов-на-Дону", "Ростова-на-Дону", "ростов\\w*(?:[- ]на[- ]дону)?", 47.2357, 39.7015, "Ростов · вокзал", null),
            city("Казань", "Казани", "казан\\w*", 55.7963, 49.1088, "Казань · вокзал", "Казань · аэропорт"),
            city("Нижний Новгород", "Нижнего Новгорода", "нижн\\w* новгород\\w*|нижн\\w*", 56.2965, 43.9361,
                    "Нижний Новгород · вокзал", "Нижний Новгород · Стригино"),
            city("Воронеж", "Воронежа", "воронеж\\w*", 51.6608, 39.2003, "Воронеж · вокзал", "Воронеж · аэропорт"),
            city("Самара", "Самары", "самар\\w*", 53.1959, 50.1002, "Самара · вокзал", "Самара · Курумоч"),
            city("Волгоград", "Волгограда", "волгоград\\w*", 48.7080, 44.5133, "Волгоград · вокзал", "Волгоград · аэропорт"),
            city("Екатеринбург", "Екатеринбурга", "екатеринбург\\w*|екб", 56.8389, 60.6057, "Екатеринбург · вокзал", "Екатеринбург · Кольцово"),
            city("Уфа", "Уфы", "уф[аыуе]", 54.7388, 55.9721, "Уфа · вокзал", "Уфа · аэропорт"),
            city("Пермь", "Перми", "перм\\w*", 58.0105, 56.2502, "Пермь · вокзал", "Пермь · Большое Савино"),
            city("Челябинск", "Челябинска", "челябинск\\w*", 55.1644, 61.4368, "Челябинск · вокзал", "Челябинск · аэропорт"),
            city("Новосибирск", "Новосибирска", "новосибирск\\w*", 55.0084, 82.9357, "Новосибирск · вокзал", "Новосибирск · Толмачёво"),
            city("Сочи", "Сочи", "сочи", 43.5855, 39.7231, "Сочи · вокзал", "Сочи · аэропорт"),
            city("Симферополь", "Симферополя", "симферопол\\w*", 44.9521, 34.1024, "Симферополь · вокзал", null),
            city("Севастополь", "Севастополя", "севастопол\\w*", 44.6167, 33.5254, "Севастополь · вокзал", null));

    /** Названия для кнопок-ответов. */
    static final List<String> POPULAR = List.of("Москва", "Санкт-Петербург", "Краснодар");

    private Places() {
    }

    static Optional<City> origin(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("^(?:отправляемся|едем|летим|выезжаем|живем|живём)?\\s*(?:из|с)?\\s+", "")
                .replaceAll("[.!,]+$", "").strip();
        for (City c : ORIGINS) {
            if (c.pattern().matcher(cleaned).matches()) {
                return Optional.of(c);
            }
        }
        // «Москва, но можем из Питера» — берём первое известное слово или пару слов
        String[] words = cleaned.split("[\\s,;]+");
        for (int i = 0; i < words.length; i++) {
            for (City c : ORIGINS) {
                if (c.pattern().matcher(words[i]).matches()
                        || i + 1 < words.length && c.pattern().matcher(words[i] + " " + words[i + 1]).matches()) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    private static final Point SIMFEROPOL_STATION = new Point(44.9496, 34.1036);

    /** Где у места отдыха вокзал и аэропорт. Если город не в справочнике — считаем, что они в самом городе. */
    static Arrival arrival(String country, String region, String city, Point hotel) {
        boolean russia = "Россия".equals(country);
        if (russia && ("Крым".equals(region) || in(city, "Ялта", "Алушта", "Гурзуф", "Севастополь", "Евпатория",
                "Феодосия", "Судак", "Симферополь"))) {
            boolean sevastopol = "Севастополь".equals(city);
            return new Arrival(sevastopol ? "Севастополь · вокзал" : "Симферополь · вокзал",
                    sevastopol ? new Point(44.6060, 33.5330) : SIMFEROPOL_STATION, null, null,
                    "Аэропорт Симферополя закрыт с февраля 2022 года — самолётом в Крым не долететь",
                    true, true, "В Крым — поездом «Таврия» или на машине через Крымский мост");
        }
        if (russia && in(city, "Анапа", "Геленджик")) {
            return new Arrival("Анапа · вокзал", new Point(44.8890, 37.3320), null, null,
                    "Аэропорт Анапы закрыт с февраля 2022 года", true, true, null);
        }
        if (russia && ("Сочи".equals(city) || "Сочи".equals(region))) {
            return new Arrival("Сочи · вокзал", new Point(43.5917, 39.7280), "Сочи · аэропорт", new Point(43.4499, 39.9566),
                    null, true, true, null);
        }
        if (russia && "Калининград".equals(city)) {
            return new Arrival("Калининград · Южный вокзал", new Point(54.6970, 20.5100), "Калининград · Храброво",
                    new Point(54.8900, 20.5926), null, false, false,
                    "В Калининград удобнее самолётом: поезд и машина идут транзитом через Литву, нужны документы");
        }
        Point near = new Point(hotel.lat() + 0.12, hotel.lon() + 0.10);   // ≈ 15 км — типичная дорога до аэропорта
        Point station = new Point(hotel.lat() + 0.03, hotel.lon() + 0.02); // ≈ 4 км — вокзал обычно в городе
        String name = city == null ? country : city;
        if (russia) {
            return new Arrival(name + " · вокзал", station, name + " · аэропорт", near, null, true, true, null);
        }
        return new Arrival(null, null, name + " · аэропорт", near, null, false, false,
                "За границу считаем только перелёт; визы и въезд — в карточке отеля");
    }

    private static boolean in(String value, String... options) {
        if (value == null) {
            return false;
        }
        for (String o : options) {
            if (o.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** Расстояние по прямой, км. */
    static double km(Point a, Point b) {
        double r = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }
}
