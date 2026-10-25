package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;

import java.util.Locale;

/** Общие подписи для фактов. */
final class Format {

    private Format() {
    }

    static String meters(double m) {
        if (m < 1000) {
            return Math.round(m / 10.0) * 10 + " м";
        }
        return String.format(Locale.ROOT, "%.1f км", m / 1000).replace('.', ',');
    }

    private static final java.util.regex.Pattern TYPED = java.util.regex.Pattern.compile(
            "^(Ресторан|Кафе|Кофейня|Бар|Лобби-бар|Паб|Пляж|Аптека|Магазин|Супермаркет|Парк|Сквер|Сад|Музей|Остановка|Рынок|Столовая|Пекарня)\\b.*",
            java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);

    /** «кафе «Марина»», «Ресторан «Крымъ»» (если тип уже в названии), «кафе без названия». */
    static String label(OsmElement e, String kind) {
        String name = cleanName(e.name());
        if (name == null) {
            return kind + " без названия";
        }
        if (TYPED.matcher(name).matches()) {
            int space = name.indexOf(' ');
            if (space > 0 && !name.contains("«")) {
                return name.substring(0, space) + " «" + name.substring(space + 1) + "»";
            }
            return name;
        }
        return kind + " «" + name + "»";
    }

    /** Убираем ASCII-кавычки из названий OSM: {@code Пляж "Ореанда"} → {@code Пляж Ореанда}. */
    static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.replace("\"", "").replace("«", "").replace("»", "").replaceAll("\\s+", " ").strip();
        return n.isEmpty() ? null : n;
    }

    static String minutes(int m) {
        return m + " мин пешком";
    }
}
