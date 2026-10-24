package ru.chevteam.onestep.intent.rules;

import ru.chevteam.onestep.intent.Destination;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Словарь направлений: основа слова → направление. Основы ловят все падежи
 * («Турция», «в Турцию», «по Турции»; «Питер», «в Питере»).
 * <p>
 * Три уровня: страна, регион («Крым», «Карелия», «Золотое кольцо») и город.
 * Если названо несколько уровней («в Крым, в Ялту»), выбирается самый точный.
 */
final class DestinationDictionary {

    private record Entry(Pattern pattern, Destination destination) {
    }

    private static final String TURKEY = "Турция";
    private static final String RUSSIA = "Россия";
    private static final String THAILAND = "Таиланд";
    private static final String UAE = "ОАЭ";
    private static final String EGYPT = "Египет";
    private static final String VIETNAM = "Вьетнам";
    private static final String GEORGIA = "Грузия";
    private static final String ABKHAZIA = "Абхазия";
    private static final String MALDIVES = "Мальдивы";
    private static final String INDONESIA = "Индонезия";
    private static final String SRI_LANKA = "Шри-Ланка";
    private static final String CHINA = "Китай";
    private static final String JAPAN = "Япония";
    private static final String KOREA = "Южная Корея";
    private static final String CAMBODIA = "Камбоджа";
    private static final String LAOS = "Лаос";
    private static final String SINGAPORE = "Сингапур";
    private static final String MALAYSIA = "Малайзия";
    private static final String PHILIPPINES = "Филиппины";
    private static final String INDIA = "Индия";
    private static final String BELARUS = "Беларусь";
    private static final String KAZAKHSTAN = "Казахстан";
    private static final String UZBEKISTAN = "Узбекистан";
    private static final String ARMENIA = "Армения";
    private static final String AZERBAIJAN = "Азербайджан";
    private static final String KYRGYZSTAN = "Кыргызстан";
    private static final String AUSTRALIA = "Австралия";
    private static final String NEW_ZEALAND = "Новая Зеландия";

    private static final String CRIMEA = "Крым";
    private static final String KUBAN = "Краснодарский край";
    private static final String KALININGRAD_REGION = "Калининградская область";
    private static final String LENINGRAD_REGION = "Ленинградская область";
    private static final String SPB = "Санкт-Петербург";
    private static final String GOLDEN_RING = "Золотое кольцо";
    private static final String KARELIA = "Карелия";
    private static final String ALTAI = "Алтай";
    private static final String BAIKAL = "Байкал";
    private static final String KMV = "Кавказские Минеральные Воды";
    private static final String DAGESTAN = "Дагестан";

    private static final List<Entry> ENTRIES = List.of(
            // Турция
            city("анталь\\w*", TURKEY, "Анталья"),
            city("кемер\\w*", TURKEY, "Кемер"),
            city("белек\\w*", TURKEY, "Белек"),
            city("сиде", TURKEY, "Сиде"),
            city("алань\\w*", TURKEY, "Аланья"),
            city("стамбул\\w*", TURKEY, "Стамбул"),
            // Россия: города
            city("сочи|адлер\\w*", RUSSIA, KUBAN, "Сочи"),
            city("красн\\w* полян\\w*|роз\\w* хутор\\w*", RUSSIA, KUBAN, "Красная Поляна"),
            city("анап\\w*|джемете", RUSSIA, KUBAN, "Анапа"),
            city("геленджик\\w*", RUSSIA, KUBAN, "Геленджик"),
            city("питер\\w*|петербург\\w*|санкт\\S*|спб", RUSSIA, SPB, "Санкт-Петербург"),
            city("кронштадт\\w*", RUSSIA, SPB, "Кронштадт"),
            city("петергоф\\w*|петродворц\\w*", RUSSIA, SPB, "Петергоф"),
            city("выборг\\w*", RUSSIA, LENINGRAD_REGION, "Выборг"),
            city("москв\\w*", RUSSIA, "Москва", "Москва"),
            city("казан\\w*", RUSSIA, "Татарстан", "Казань"),
            city("калининград(?!ск)\\w*|кенигсберг\\w*", RUSSIA, KALININGRAD_REGION, "Калининград"),
            city("светлогорск\\w*|раушен\\w*", RUSSIA, KALININGRAD_REGION, "Светлогорск"),
            city("зеленоградск\\w*", RUSSIA, KALININGRAD_REGION, "Зеленоградск"),
            city("ялт\\w*", RUSSIA, CRIMEA, "Ялта"),
            city("алушт\\w*", RUSSIA, CRIMEA, "Алушта"),
            city("евпатори\\w*", RUSSIA, CRIMEA, "Евпатория"),
            city("севастопол\\w*|балаклав\\w*|фиолент\\w*", RUSSIA, CRIMEA, "Севастополь"),
            city("судак(?:е|а|у)?|нов\\w* свет\\w*", RUSSIA, CRIMEA, "Судак"),
            city("велик\\w* новгород\\w*", RUSSIA, "Новгородская область", "Великий Новгород"),
            city("псков\\w*", RUSSIA, "Псковская область", "Псков"),
            city("суздал\\w*", RUSSIA, GOLDEN_RING, "Суздаль"),
            city("ярославл\\w*", RUSSIA, GOLDEN_RING, "Ярославль"),
            city("нижн\\w* новгород\\w*", RUSSIA, "Нижегородская область", "Нижний Новгород"),
            city("петрозаводск\\w*|кижи", RUSSIA, KARELIA, "Петрозаводск"),
            city("сортавал\\w*|рускеал\\w*", RUSSIA, KARELIA, "Сортавала"),
            city("чемал\\w*", RUSSIA, ALTAI, "Чемал"),
            city("листвянк\\w*", RUSSIA, BAIKAL, "Листвянка"),
            city("мурманск\\w*|териберк\\w*", RUSSIA, "Мурманская область", "Мурманск"),
            city("кисловодск\\w*|пятигорск\\w*|ессентук\\w*", RUSSIA, KMV, "Кисловодск"),
            city("дербент\\w*", RUSSIA, DAGESTAN, "Дербент"),
            city("владивосток\\w*", RUSSIA, "Приморский край", "Владивосток"),
            city("махачкал\\w*|сарыкум\\w*|сулакск\\w*", RUSSIA, DAGESTAN, "Махачкала"),
            city("екатеринбург\\w*|\\bекб\\b", RUSSIA, "Свердловская область", "Екатеринбург"),
            city("новосибирск\\w*", RUSSIA, "Новосибирская область", "Новосибирск"),
            city("феодоси\\w*", RUSSIA, CRIMEA, "Феодосия"),
            city("коктебел\\w*|кара-?даг\\w*", RUSSIA, CRIMEA, "Коктебель"),
            city("гурзуф\\w*|аю-?даг\\w*", RUSSIA, CRIMEA, "Гурзуф"),
            // Таиланд, ОАЭ, Египет
            city("пхукет\\w*", THAILAND, "Пхукет"),
            city("паттай\\w*", THAILAND, "Паттайя"),
            city("самуи", THAILAND, "Самуи"),
            city("бангкок\\w*", THAILAND, "Бангкок"),
            city("краби", THAILAND, "Краби"),
            city("дуба[йе]\\w*", UAE, "Дубай"),
            city("абу-?\\s?даби", UAE, "Абу-Даби"),
            city("хургад\\w*", EGYPT, "Хургада"),
            city("шарм\\S*", EGYPT, "Шарм-эль-Шейх"),
            // Кавказ
            city("батуми", GEORGIA, "Батуми"),
            city("тбилиси", GEORGIA, "Тбилиси"),
            city("гагр\\w*", ABKHAZIA, "Гагра"),
            city("ереван\\w*", ARMENIA, "Ереван"),
            city("дилижан\\w*", ARMENIA, "Дилижан"),
            city("севан\\w*", ARMENIA, "Севан"),
            city("баку", AZERBAIJAN, "Баку"),
            city("шеки", AZERBAIJAN, "Шеки"),
            // Беларусь
            city("минск\\w*", BELARUS, "Минск"),
            city("брест\\w*|беловежск\\w*", BELARUS, "Брест"),
            city("гродн\\w*", BELARUS, "Гродно"),
            city("несвиж\\w*", BELARUS, "Несвиж"),
            city("витебск\\w*", BELARUS, "Витебск"),
            // Казахстан, Кыргызстан, Узбекистан
            city("алмат\\w*|алма-?ат\\w*|медео|медеу", KAZAKHSTAN, "Алматы"),
            city("астан\\w*|нур-?султан\\w*", KAZAKHSTAN, "Астана"),
            city("бурабай\\w*|борово\\w*", KAZAKHSTAN, "Бурабай"),
            city("актау", KAZAKHSTAN, "Актау"),
            city("чолпон\\S*|иссык\\S*(?:\\s?кул\\w*)?", KYRGYZSTAN, "Чолпон-Ата"),
            city("бишкек\\w*", KYRGYZSTAN, "Бишкек"),
            city("самарканд\\w*", UZBEKISTAN, "Самарканд"),
            city("бухар\\w*", UZBEKISTAN, "Бухара"),
            city("хив(?:а|е|у|ой)", UZBEKISTAN, "Хива"),
            city("ташкент\\w*", UZBEKISTAN, "Ташкент"),
            // Юго-Восточная и Восточная Азия
            city("нячанг\\w*|камран\\w*", VIETNAM, "Нячанг"),
            city("фукуок\\w*", VIETNAM, "Фукуок"),
            city("дананг\\w*|хойан\\w*", VIETNAM, "Дананг"),
            city("ханое?\\w*", VIETNAM, "Ханой"),
            city("хошимин\\w*|сайгон\\w*", VIETNAM, "Хошимин"),
            city("пекин\\w*", CHINA, "Пекин", "Пекин"),
            city("шанха[йе]\\w*", CHINA, "Шанхай", "Шанхай"),
            city("сань[яеию]|хайнан\\w*", CHINA, "Хайнань", "Санья"),
            city("гуанчжоу|кантон\\w*", CHINA, "Гуандун", "Гуанчжоу"),
            city("харбин\\w*", CHINA, "Хэйлунцзян", "Харбин"),
            city("ч[эе]н(?:г)?ду|база панд", CHINA, "Сычуань", "Чэнду"),
            city("чунцин\\w*", CHINA, "Чунцин", "Чунцин"),
            city("сиан[ьяеюи]\\w*|терракотов\\w* арми\\w*", CHINA, "Шэньси", "Сиань"),
            city("гуйлин\\w*|река ли\\b", CHINA, "Гуанси", "Гуйлинь"),
            city("яншо", CHINA, "Гуанси", "Яншо"),
            city("чжанцзяцзе|улинъюан\\w*|гор\\w* аватар\\w*|аватар\\w*", CHINA, "Хунань", "Чжанцзяцзе"),
            city("ханчжоу|сиху", CHINA, "Чжэцзян", "Ханчжоу"),
            city("сучжоу", CHINA, "Цзянсу", "Сучжоу"),
            city("лицзян\\w*", CHINA, "Юньнань", "Лицзян"),
            city("эрхай|старый город дали", CHINA, "Юньнань", "Дали"),
            city("циндао", CHINA, "Шаньдун", "Циндао"),
            city("сямын\\w*|сямэн\\w*|гуланъю\\w*", CHINA, "Фуцзянь", "Сямынь"),
            city("токио", JAPAN, "Токио"),
            city("киото", JAPAN, "Киото"),
            city("осак\\w*", JAPAN, "Осака"),
            city("сеул\\w*", KOREA, "Сеул"),
            city("сием\\S*|сиемреап\\w*|ангкор\\w*", CAMBODIA, "Сием-Рип"),
            city("пномпен\\w*", CAMBODIA, "Пномпень"),
            city("луанг\\S*", LAOS, "Луангпрабанг"),
            city("вьентьян\\w*", LAOS, "Вьентьян"),
            city("сингапур\\w*|сентоз\\w*", SINGAPORE, "Сингапур"),
            city("куала\\S*", MALAYSIA, "Куала-Лумпур"),
            city("лангкав\\w*", MALAYSIA, "Лангкави"),
            city("бали|убуд\\w*|нуса-?\\s?дуа|ловин\\w*", INDONESIA, "Бали"),
            city("боракай\\w*", PHILIPPINES, "Боракай"),
            city("гоа", INDIA, "Гоа"),
            city("унаватун\\w*", SRI_LANKA, "Унаватуна"),
            city("коломбо", SRI_LANKA, "Коломбо"),
            // Австралия и Океания
            city("сидне[йяеюм]\\w*|бонди", AUSTRALIA, "Сидней"),
            city("мельбурн\\w*", AUSTRALIA, "Мельбурн"),
            city("голд-?\\s?кост\\w*", AUSTRALIA, "Голд-Кост"),
            city("кэрнс\\w*|кернс\\w*|барьерн\\w* риф\\w*|палм-?\\s?ков\\w*", AUSTRALIA, "Кэрнс"),
            city("окленд\\w*", NEW_ZEALAND, "Окленд"),
            city("куинстаун\\w*", NEW_ZEALAND, "Куинстаун"),
            // регионы
            region("крым\\w*", RUSSIA, CRIMEA),
            region("краснодарск\\w* кра\\w*|кубан\\w*", RUSSIA, KUBAN),
            region("калининградск\\w* област\\w*|янтарн\\w* кра\\w*", RUSSIA, KALININGRAD_REGION),
            region("ленинградск\\w* област\\w*|ленобласт\\w*", RUSSIA, LENINGRAD_REGION),
            region("золот\\w* кольц\\w*", RUSSIA, GOLDEN_RING),
            region("карели\\w*", RUSSIA, KARELIA),
            region("алта[йюяе]\\w*", RUSSIA, ALTAI),
            region("байкал\\w*", RUSSIA, BAIKAL),
            region("кмв|минвод\\w*|кавказск\\w* минеральн\\w* вод\\w*", RUSSIA, KMV),
            region("дагестан\\w*", RUSSIA, DAGESTAN),
            region("урал\\w*", RUSSIA, "Свердловская область"),
            region("сычуан\\w*", CHINA, "Сычуань"),
            region("юньнан\\w*", CHINA, "Юньнань"),
            region("гуанси\\w*", CHINA, "Гуанси"),
            region("хунан\\w*", CHINA, "Хунань"),
            region("чжэцзян\\w*", CHINA, "Чжэцзян"),
            region("цзянсу", CHINA, "Цзянсу"),
            region("гуандун\\w*", CHINA, "Гуандун"),
            region("шаньдун\\w*", CHINA, "Шаньдун"),
            region("фуцзян\\w*", CHINA, "Фуцзянь"),
            region("хэйлунцзян\\w*|маньчжури\\w*", CHINA, "Хэйлунцзян"),
            region("шэньси", CHINA, "Шэньси"),
            // страны
            country("росси\\w*|\\bрф\\b", RUSSIA),
            country("турц\\w*", TURKEY),
            country("та[йи]ланд\\w*", THAILAND),
            country("оаэ|эмират\\w*", UAE),
            country("египт?\\w*", EGYPT),
            country("вьетнам\\w*", VIETNAM),
            country("груз(?:ия|ию|ии|ией)", GEORGIA),
            country("абхаз\\w*", ABKHAZIA),
            country("мальдив\\w*", MALDIVES),
            country("индонези\\w*", INDONESIA),
            country("шри-?\\s?ланк\\w*", SRI_LANKA),
            country("кита(?:й|я|е|ю|ем)", CHINA),
            country("япони\\w*", JAPAN),
            country("(?:южн\\w* )?коре(?:я|ю|и|ей)", KOREA),
            country("камбодж\\w*", CAMBODIA),
            country("лаос\\w*", LAOS),
            country("малайзи\\w*", MALAYSIA),
            country("филиппин\\w*", PHILIPPINES),
            country("инди(?:я|ю|и|ей)", INDIA),
            country("беларус\\w*|белорусси\\w*|белорусь\\w*", BELARUS),
            country("казахстан\\w*", KAZAKHSTAN),
            country("узбекистан\\w*", UZBEKISTAN),
            country("армени\\w*", ARMENIA),
            country("азербайджан\\w*", AZERBAIJAN),
            country("кыргызстан\\w*|киргизи\\w*|киргиз\\w*", KYRGYZSTAN),
            country("австрали\\w*", AUSTRALIA),
            country("нов\\w* зеланди\\w*", NEW_ZEALAND));

    private DestinationDictionary() {
    }

    /**
     * Находит направление, упомянутое в тексте раньше всех остальных. Если первой названа страна
     * или регион, а дальше — город внутри них («в Таиланд, на Пхукет», «Крым, Ялта»), выбирается город.
     */
    static Optional<Match> find(String normalizedText) {
        List<Match> matches = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            Matcher m = entry.pattern().matcher(normalizedText);
            if (m.find()) {
                matches.add(new Match(entry.destination(), m.start(), m.end(), -1, -1));
            }
        }
        Match best = matches.stream().min(Comparator.comparingInt(Match::start)).orElse(null);
        if (best == null || best.destination().city() != null) {
            return Optional.ofNullable(best);
        }
        Match broad = best;
        return Optional.of(matches.stream()
                .filter(m -> m.destination().city() != null && within(m.destination(), broad.destination()))
                .min(Comparator.comparingInt(Match::start))
                .map(city -> new Match(city.destination(), city.start(), city.end(), broad.start(), broad.end()))
                .orElseGet(() -> refineRegion(broad, matches)));
    }

    /** «Россия, Крым» → Крым. */
    private static Match refineRegion(Match broad, List<Match> matches) {
        if (broad.destination().region() != null) {
            return broad;
        }
        return matches.stream()
                .filter(m -> m.destination().city() == null && m.destination().region() != null
                        && m.destination().country().equals(broad.destination().country()))
                .min(Comparator.comparingInt(Match::start))
                .map(r -> new Match(r.destination(), r.start(), r.end(), broad.start(), broad.end()))
                .orElse(broad);
    }

    private static boolean within(Destination city, Destination broad) {
        return city.country().equals(broad.country())
                && (broad.region() == null || Objects.equals(broad.region(), city.region()));
    }

    /**
     * @param extraStart второй найденный фрагмент (страна или регион при выбранном городе) или -1
     */
    record Match(Destination destination, int start, int end, int extraStart, int extraEnd) {

        boolean hasExtra() {
            return extraStart >= 0;
        }
    }

    private static Entry city(String stemRegex, String country, String city) {
        return new Entry(word(stemRegex), Destination.city(country, city));
    }

    private static Entry city(String stemRegex, String country, String region, String city) {
        return new Entry(word(stemRegex), Destination.city(country, region, city));
    }

    private static Entry region(String stemRegex, String country, String region) {
        return new Entry(word(stemRegex), Destination.region(country, region));
    }

    private static Entry country(String stemRegex, String country) {
        return new Entry(word(stemRegex), Destination.country(country));
    }

    private static Pattern word(String stemRegex) {
        return Pattern.compile("\\b(?:" + stemRegex + ")\\b", RussianText.FLAGS);
    }
}
