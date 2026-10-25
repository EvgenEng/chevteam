package ru.chevteam.onestep.enrichment.internal.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Добавить характеристику текстом» без LLM: словарь «что ищем» → фильтр OSM и разбор «как далеко».
 * Покрывает частые формулировки; всё прочее можно передать модели, а этот разбор — её страховка.
 * <ul>
 *   <li>«аптека в 5 минутах» → NEAREST amenity=pharmacy, 300 м;</li>
 *   <li>«нет ночных клубов рядом» → ABSENT amenity=nightclub, 300 м;</li>
 *   <li>«много кафе рядом» → COUNT amenity=cafe|restaurant, 400 м, от 5.</li>
 * </ul>
 */
public final class TextRecipes {

    private static final int FLAGS = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Map<Pattern, String[]> TARGETS = new LinkedHashMap<>();

    static {
        put("аптек", "amenity=pharmacy", "аптека");
        put("больниц|поликлиник|клиник|травмпункт|врач", "amenity=hospital|clinic|doctors", "клиника");
        put("ночн\\w* клуб|дискотек", "amenity=nightclub", "клуб");
        put("\\bбар|\\bпаб", "amenity=bar|pub|nightclub", "бар");
        put("кофейн|кофе", "amenity=cafe", "кофейня");
        put("ресторан|кафе|поесть|столов", "amenity=cafe|restaurant|fast_food", "кафе");
        put("супермаркет|продукт|магазин", "shop=supermarket|convenience", "магазин");
        put("рын(ок|к)", "shop=marketplace;amenity=marketplace", "рынок");
        put("остановк|автобус|троллейбус|маршрутк", "highway=bus_stop;public_transport=platform", "остановка");
        put("стройк|строительств", "landuse=construction", "стройка");
        put("парковк", "amenity=parking", "парковка");
        put("детск\\w* площадк", "leisure=playground", "детская площадка");
        put("парк|сквер|\\bсад", "leisure=park|garden", "парк");
        put("лестниц|ступен", "highway=steps", "лестница");
        put("пляж", "natural=beach;leisure=beach_resort", "пляж");
        put("музе", "tourism=museum", "музей");
        put("смотров|видов\\w* площадк", "tourism=viewpoint", "смотровая");
        put("банкомат|\\bбанк", "amenity=atm|bank", "банкомат");
        put("спортзал|фитнес", "leisure=fitness_centre", "спортзал");
        put("заправк|\\bазс", "amenity=fuel", "заправка");
        put("храм|церк|мечет|синагог", "amenity=place_of_worship", "храм");
    }

    private static final Pattern MINUTES = Pattern.compile("(\\d{1,2})\\s*мин", FLAGS);
    private static final Pattern METERS = Pattern.compile("(\\d{2,4})\\s*(?:м\\b|метр)", FLAGS);
    private static final Pattern KM = Pattern.compile("(\\d{1,2}(?:[.,]\\d)?)\\s*км", FLAGS);
    private static final Pattern NEGATION = Pattern.compile("\\b(?:нет|без|вдали|подальше|далеко от)\\b", FLAGS);
    private static final Pattern MANY = Pattern.compile("\\b(?:много|несколько|выбор)\\b", FLAGS);

    private TextRecipes() {
    }

    private static void put(String regex, String filter, String noun) {
        TARGETS.put(Pattern.compile(regex, FLAGS), new String[] {filter, noun});
    }

    /** Пусто — не поняли, что искать: тогда нужна модель или уточнение у админа. */
    public static Optional<Recipe> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        String[] target = null;
        for (Map.Entry<Pattern, String[]> e : TARGETS.entrySet()) {
            if (e.getKey().matcher(t).find()) {
                target = e.getValue();
                break;
            }
        }
        if (target == null) {
            return Optional.empty();
        }
        boolean absent = NEGATION.matcher(t).find();
        boolean many = !absent && MANY.matcher(t).find();
        int radius = radius(t, absent ? 300 : many ? 400 : 500);
        Recipe.Tool tool = absent ? Recipe.Tool.ABSENT : many ? Recipe.Tool.COUNT : Recipe.Tool.NEAREST;
        String clean = text.strip();
        String title = Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        return Optional.of(new Recipe(code(target[1], tool), title, tool, target[0], radius, many ? 5 : 1, target[1]));
    }

    private static int radius(String t, int fallback) {
        Matcher m = MINUTES.matcher(t);
        if (m.find()) {
            // минуты пешком → прямое расстояние: 75 м/мин, улицы петляют × 1,25
            return clamp((int) Math.round(Integer.parseInt(m.group(1)) * 75 / 1.25));
        }
        m = METERS.matcher(t);
        if (m.find()) {
            return clamp(Integer.parseInt(m.group(1)));
        }
        m = KM.matcher(t);
        if (m.find()) {
            return clamp((int) Math.round(Double.parseDouble(m.group(1).replace(',', '.')) * 1000));
        }
        return fallback;
    }

    private static int clamp(int meters) {
        return Math.max(50, Math.min(5000, meters));
    }

    private static String code(String noun, Recipe.Tool tool) {
        StringBuilder latin = new StringBuilder();
        for (char c : noun.toCharArray()) {
            latin.append(translit(c));
        }
        String base = latin.toString().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
        return "CUSTOM_" + (tool == Recipe.Tool.ABSENT ? "NO_" : tool == Recipe.Tool.COUNT ? "MANY_" : "") + base;
    }

    private static String translit(char c) {
        String abc = "абвгдежзийклмнопрстуфхцчшщъыьэюя";
        String[] lat = {"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f",
                "h", "ts", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};
        int i = abc.indexOf(c);
        return i >= 0 ? lat[i] : String.valueOf(c);
    }
}
