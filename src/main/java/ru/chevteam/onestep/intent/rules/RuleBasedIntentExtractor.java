package ru.chevteam.onestep.intent.rules;

import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TransitKind;
import ru.chevteam.onestep.intent.TransportPreference;
import ru.chevteam.onestep.intent.Wildlife;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.chevteam.onestep.intent.rules.RussianText.FLAGS;
import static ru.chevteam.onestep.intent.rules.RussianText.NUMBER;

/**
 * Детерминированный разбор запроса на русском языке.
 * <p>
 * Работает всегда: без сети, за микросекунды, предсказуемо. Используется как
 * fallback при недоступности LLM и как источник «точных» фактов для валидации ответа LLM.
 * <p>
 * Найденные фрагменты «вычёркиваются» из рабочего текста, чтобы одно и то же число
 * не было распознано дважды (например, «до 15 минут» не станет бюджетом).
 */
public final class RuleBasedIntentExtractor implements IntentExtractor {

    static final int DEFAULT_NIGHTS = 7;
    static final int DEFAULT_WEEKEND_NIGHTS = 2;
    static final int DEFAULT_ADULTS = 2;
    static final int UNKNOWN_CHILD_AGE = 8;
    static final int DEFAULT_WALK_MINUTES = 15;

    private static final String MONTH_WORD = "(январ[ьяе]|феврал[ьяе]|март[ае]?|апрел[ьяе]|ма[йяе]|июн[ьяе]|"
            + "июл[ьяе]|август[ае]?|сентябр[ьяе]|октябр[ьяе]|ноябр[ьяе]|декабр[ьяе])";

    private static final Pattern ORIGIN = p("\\bиз\\s+(москв\\w*|питер\\w*|петербург\\w*|санкт\\S*|спб|казан\\w*"
            + "|екатеринбург\\w*|новосибирск\\w*|нижн\\w* новгород\\w*|самар\\w*)");
    private static final Pattern DIRECT_FLIGHT = p("\\bпрям(?:ой|ым|ые|ого|ую|ых)\\b|\\bбез пересад\\w*");

    private static final Pattern DATE_RANGE = p("\\b(?:с\\s*)?(\\d{1,2})\\s*(?:по|-|–|до)\\s*(\\d{1,2})\\s+" + MONTH_WORD + "\\b");
    private static final Pattern MONTH = p("\\b" + MONTH_WORD + "\\b");
    private static final Pattern WEEKEND = p("\\bвыходн\\w*");

    private static final Pattern NIGHTS = p("\\b" + NUMBER + "\\s*ноч\\w*");
    private static final Pattern DAYS_AND_NIGHTS = p("\\b" + NUMBER + "\\s*сут\\w*");
    private static final Pattern DAYS = p("\\b" + NUMBER + "\\s*(?:дн\\w*|день)\\b");
    private static final Pattern WEEKS = p("\\b(?:" + NUMBER + "\\s*)?недел\\w*");

    private static final Pattern PROXIMITY_TO = p("\\bдо\\s+((?:\\p{L}[\\p{L}-]*\\s+){0,2}\\p{L}[\\p{L}-]{2,})\\s+пешком(?:\\s+минут\\w*)?(?:\\s+за)?(?:\\s+(\\d{1,3}))?(?:\\s*мин\\w*)?");
    private static final Pattern PROXIMITY_MINUTES_FIRST = p("\\b(\\d{1,3})\\s*мин\\w*\\s+(?:пешком|ходьбы)\\s+(?:до|от)\\s+(\\p{L}[\\p{L}-]{2,})");
    private static final Pattern PROXIMITY_NEAR = p("\\b(?:рядом с|возле|около|недалеко от)\\s+(\\p{L}[\\p{L}-]{2,}(?:\\s+\\p{L}[\\p{L}-]{3,})?)");
    /** «Хочу к пандам», «поближе к Эрмитажу». Море и пляж сюда не попадают — для них свои правила. */
    private static final Pattern PROXIMITY_WANT = p("\\b(?:хочу|хотим|поближе|съездить|сходить|попасть)\\s+к\\s+(\\p{L}[\\p{L}-]{3,})");
    /** «Хочу в Урюпинск» — название, которого нет в словаре направлений. */
    private static final Pattern UNKNOWN_PLACE = Pattern.compile("(?:^|[\\s,.!?])(?:в|во|на)\\s+(\\p{Lu}[\\p{L}-]{3,})");
    private static final Pattern SEA_OR_BEACH = p("^(?:мор|пляж|берег)\\w*");
    private static final Pattern CENTER_WORD = p("^центр\\w*");

    private static final Pattern BUDGET = p("\\b(?:до|не дороже|не более|максимум|в пределах|бюджет(?:ом)?(?:\\s+до)?|за)\\s*"
            + "(\\d[\\d ]*(?:[.,]\\d+)?)\\s*(тысяч\\w*|тыс\\.?|т\\.?р\\.?|к|k|млн)?(?![\\p{L}\\d])");
    private static final Pattern BUDGET_SHORT = p("\\b(\\d{2,4})\\s*(к|k|тыс\\.?|тысяч\\w*)(?![\\p{L}\\d])");
    private static final Pattern NOT_MONEY_UNIT = p("^\\s*(?:мин|км|м\\b|метр|звезд|зв\\b|лет|год|ноч|дн|день|сут|час|недел|" + MONTH_WORD + ")");
    private static final Pattern PER_NIGHT = p("^\\s*(?:₽|руб\\w*|р\\.?)?\\s*(?:за|в|/)\\s*(?:ночь|сутки)\\b");

    private static final Pattern STARS_DIGIT = p("\\b([1-5])\\s*(?:\\*|★|⭐|звезд\\w*|зв\\.?)");
    private static final Pattern STARS_WORD = p("\\b(пяти|четыр[её]х|тр[её]х)\\s*-?\\s*звезд\\w*");

    private static final Pattern ADULTS = p("\\b" + NUMBER + "\\s*взросл\\w*");
    private static final Pattern SOLO = p("\\b(?:один|одна)\\s+(?:еду|поеду|лечу|путешеству\\w*)\\b|\\b(?:еду|поеду|лечу)\\s+(?:один|одна)\\b"
            + "|\\bсоло\\b|\\bв одиночку\\b|\\bодному\\b");
    private static final Pattern PARTY_SIZE = p("\\bнас\\s+" + NUMBER + "\\b|\\b" + NUMBER + "\\s+человек\\w*");
    private static final Pattern COUPLE = p("\\bвдвоем\\b|\\bна двоих\\b|\\bс (?:женой|мужем|девушкой|парнем|подругой|другом)\\b");
    private static final String CHILD_WORD = "(?:ребен(?:ок|ка|ку|ком)|дет(?:и|ей|ям|ьми)|сын\\w*|доч(?:ь|ка|кой|ерью|ки|ерьми)|малыш\\w*)";
    private static final Pattern CHILDREN_WITH_AGES = p("\\b(?:" + NUMBER + "\\s+)?" + CHILD_WORD
            + "\\s*,?\\s*(?:(?:возраст\\w*|по)\\s*)?((?:\\d{1,2}\\s*(?:,|и)\\s*)*\\d{1,2})\\s*(?:лет|год\\w*|мес\\w*)\\b");
    private static final Pattern CHILDREN_NO_AGE = p("\\b(?:" + NUMBER + "\\s+)?" + CHILD_WORD + "\\b");
    private static final Pattern AGE_LIST_SPLIT = p("\\s*(?:,|и)\\s*");

    private static final Map<MealPlan, Pattern> MEAL_PATTERNS = linkedMap(
            MealPlan.ALL_INCLUSIVE, p("вс[её] включено|all\\s*inclusive|ультра вс\\w*|\\bu?ai\\b|олл инклюзив"),
            MealPlan.HALF_BOARD, p("полупансион|\\bhb\\b|завтрак\\w*\\s*(?:и|\\+)\\s*ужин"),
            MealPlan.FULL_BOARD, p("полн\\w* пансион|\\bfb\\b|тр[её]хразов\\w*"),
            MealPlan.BREAKFAST, p("завтрак|\\bbb\\b"),
            MealPlan.ROOM_ONLY, p("без питания"));

    private static final Map<Amenity, Pattern> AMENITY_PATTERNS = linkedMap(
            Amenity.AQUAPARK, p("аквапарк\\w*|водн\\w* горк\\w*|\\bгорк(?:и|ами|ой)\\b"),
            Amenity.POOL, p("бассейн\\w*"),
            Amenity.KIDS_CLUB, p("детск\\w* клуб\\w*|аниматор\\w*|анимаци\\w*"),
            Amenity.SPA, p("\\bспа\\b|\\bspa\\b|саун\\w*|хаммам\\w*"),
            Amenity.MOTO_RENTAL, p("(?:прокат\\w*|аренд\\w*|взять)\\s+(?:\\p{L}+\\s+)?(?:мотобайк|байк|скутер|мопед|мотоцикл)\\w*"
                    + "|покатат\\w*\\s+на\\s+(?:мотобайк|байк|скутер|мопед)\\w*|(?:байк|скутер|мопед)\\w*\\s+напрокат|напрокат\\s+(?:байк|скутер|мопед)\\w*"),
            Amenity.CAR_RENTAL, p("(?:прокат\\w*|аренд\\w*|взять)\\s+(?:\\p{L}+\\s+)?(?:авто(?!бус)|машин|автомобил)\\w*"
                    + "|(?:машин|авто(?!бус)|автомобил)\\w*\\s+напрокат|rent\\s*a\\s*car|каршеринг\\w*"),
            Amenity.BIKE_RENTAL, p("велопрокат\\w*|(?:прокат\\w*|аренд\\w*)\\s+(?:велосипед|велик)\\w*|велосипед\\w*"),
            Amenity.MOTO_PARKING, p("(?:парковк|стоянк|паркинг)\\w*\\s+(?:для\\s+)?(?:мото|байк|скутер)\\w*"
                    + "|на\\s+(?:сво\\w*\\s+|личн\\w*\\s+)?(?:мотоцикл|мотобайк|байк)\\w*|мотопутешеств\\w*"),
            Amenity.EV_CHARGING, p("зарядк\\w*\\s+(?:для\\s+)?электро\\w*|электромобил\\w*|электрокар\\w*|\\bтесл\\w*"),
            Amenity.PARKING, p("парковк\\w*|паркинг\\w*|стоянк\\w* для (?:машин|авто)\\w*"),
            Amenity.GYM, p("фитнес\\w*|спортзал\\w*|тренаж\\w*"),
            Amenity.RESTAURANT, p("(?:\\bс|есть|сво\\w*)\\s+ресторан\\w*|ресторан\\w*\\s+(?:в|при)\\s+отел\\w*"),
            Amenity.BAR, p("лобби-бар\\w*|\\bбар(?:ом|а|у|е|ы)?\\b"));

    private static final Map<DietNeed, Pattern> DIET_PATTERNS = linkedMap(
            DietNeed.HINDU, p("индуист\\w*(?:\\s+(?:кухн|еда|меню|питани)\\w*)?|\\bхинду\\w*|без говядины"
                    + "|индийск\\w*\\s+(?:вегетарианск\\w*\\s+)?(?:кухн|еда|меню)\\w*"),
            DietNeed.VEGAN, p("веган\\w*(?:\\s+(?:кухн|меню|еда|питани|блюд)\\w*)?|\\bvegan\\b"),
            DietNeed.VEGETARIAN, p("вегетариан\\w*(?:\\s+(?:кухн|меню|еда|питани|блюд)\\w*)?|без мяса|постн\\w* меню"),
            DietNeed.HALAL, p("халя[лль]\\w*|\\bhalal\\b|(?:мусульманск|исламск)\\w*\\s+(?:кухн|еда|питани|меню)\\w*"),
            DietNeed.KOSHER, p("кошер\\w*(?:\\s+(?:кухн|еда|питани|меню)\\w*)?|\\bkosher\\b"),
            DietNeed.GLUTEN_FREE, p("без\\s+глютен\\w*|безглютен\\w*(?:\\s+(?:кухн|еда|питани|меню)\\w*)?|целиаки\\w*"),
            DietNeed.DIETARY, p("диетическ\\w*(?:\\s+(?:кухн|питани|меню|стол)\\w*)?|\\bдиет[аеуы]\\b|правильн\\w* питани\\w*"),
            DietNeed.KIDS_MENU, p("детск\\w* меню"));

    /** «Рядом кафе для завтрака», «вкусные кафешки поблизости», «где поужинать рядом». */
    private static final String NEAR_WORD = "(?:рядом|поблизости|неподалеку|недалеко|вокруг|в шаговой доступности|в пешей доступности)";
    private static final String EATERY = "(?:кафе\\w*|кафешк\\w*|кофейн\\w*|ресторан\\w*|столов\\w*|едальн\\w*|забегаловк\\w*)";
    private static final String MEAL_TIME = "(?:\\s+(?:для|на)\\s+(?:завтрак|ужин|обед)\\w*(?:\\s+(?:и|или)\\s+(?:завтрак|ужин|обед)\\w*)?)?";
    private static final Pattern FOOD_NEARBY = p(NEAR_WORD + "\\s+(?:(?:есть|были|много|хорош\\w*|вкусн\\w*|недорог\\w*)\\s+){0,2}" + EATERY + MEAL_TIME
            + "|(?:(?:вкусн\\w*|хорош\\w*|недорог\\w*|уютн\\w*)\\s+){0,2}" + EATERY + MEAL_TIME + "\\s+" + NEAR_WORD
            + "|где\\s+(?:вкусно\\s+)?(?:по)?(?:завтракать|ужинать|обедать|есть|поесть)\\s+" + NEAR_WORD
            + "|(?:позавтракать|поужинать|пообедать|поесть)\\s+" + NEAR_WORD);

    /** «На своей машине» — значит, нужна парковка. */
    private static final Pattern OWN_CAR = p("\\b(?:на|своим|личн\\w*)\\s+(?:сво\\w*\\s+|личн\\w*\\s+)?(?:машин|авто(?!бус)|автомобил)\\w*");

    private static final String TRANSIT_WORD = "(аэропорт\\w*|автовокзал\\w*|автостанци\\w*|(?:ж/?д\\s+|железнодорожн\\w*\\s+)?вокзал\\w*"
            + "|(?:станци\\w*\\s+)?метро)";
    private static final String DURATION = "(\\d{1,3}(?:[.,]5)?|полчаса|полтора|час|два|три|четыре|пять)(?:\\s*-?\\s*(минут\\w*|мин\\b|час\\w*|ч\\b))?";
    private static final Pattern TRANSIT_MAX = p("\\b(?:до|от|из)\\s+" + TRANSIT_WORD + "\\s+(?:пешком\\s+|на машине\\s+|на такси\\s+|ехать\\s+)?"
            + "(?:(?:не\\s+)?(?:больше|более|дольше|дальше)\\s+|максимум\\s+|в пределах\\s+|за\\s+|около\\s+|минут\\w*\\s+|до\\s+)?" + DURATION);
    private static final Pattern TRANSIT_MINUTES_FIRST = p("\\b" + DURATION + "\\s+(?:езды\\s+|пути\\s+|пешком\\s+|на такси\\s+)?(?:до|от)\\s+" + TRANSIT_WORD);
    private static final Pattern TRANSIT_NEAR = p("\\b(?:рядом с|близко к|недалеко от|возле|около|у самого|у)\\s+" + TRANSIT_WORD
            + "|\\b(?:в шаговой|в пешей) доступности от\\s+" + TRANSIT_WORD);
    private static final Pattern SHORT_TRANSFER = p("\\b(?:коротк\\w*|быстр\\w*|недолг\\w*)\\s+трансфер\\w*"
            + "|трансфер\\w*\\s+(?:не\\s+)?(?:больше|более|дольше|до)?\\s*" + DURATION);

    private static final Map<SoftPreference, Pattern> SOFT_PATTERNS = linkedMap(
            SoftPreference.QUIET, p("\\bтих\\w*|\\bспокойн\\w*|без шума|не шумн\\w*|без (?:баров|клубов|дискотек|ночной жизни|тусовок)|подальше от (?:баров|клубов|дискотек)"),
            SoftPreference.CLEAN, p("\\bчист\\w*"),
            SoftPreference.GOOD_WIFI, p("wi-?fi|вай-?фай|интернет\\w*"));

    private static final Map<BeachPreference, Pattern> BEACH_PATTERNS = linkedMap(
            BeachPreference.SANDY, p("песчан\\w*|\\bпес(?:ок|ка|ке|ком)\\b|песочн\\w*|без гальк\\w*|не галечн\\w*"),
            BeachPreference.GENTLE_ENTRY, p("мелковод\\w*|мелк\\w* (?:мор\\w*|вод\\w*)|полог\\w* (?:вход\\w*|заход\\w*|берег\\w*|дн\\w*)"
                    + "|плавн\\w* (?:вход\\w*|заход\\w*)|без (?:резк\\w* )?(?:глубин\\w*|обрыв\\w*)|неглубок\\w*"),
            BeachPreference.NO_TIDES, p("отлив\\w*|прилив\\w*"),
            BeachPreference.NO_JELLYFISH, p("медуз\\w*"),
            BeachPreference.BEACH_FUN, p("развлечени\\w* на (?:пляж|мор)\\w*|водн\\w* (?:развлечени|аттракцион|вид\\w* спорт)\\w*"
                    + "|банан\\w*|парасейл\\w*|гидроцикл\\w*|парашют\\w*"),
            BeachPreference.UNCROWDED, p("немноголюдн\\w*|малолюдн\\w*|безлюдн\\w*|без толп\\w*|не многолюдн\\w*|пуст\\w* пляж\\w*"));

    /** Порядок важен: «кайтсерфинг» должен стать кайтом, а не серфингом. */
    private static final Map<BeachActivity, Pattern> ACTIVITY_PATTERNS = linkedMap(
            BeachActivity.KITESURFING, p("\\bкайт\\w*"),
            BeachActivity.WINDSURFING, p("\\bвинд\\w*"),
            BeachActivity.SURFING, p("\\bс[еэ]рф\\w*|ловить волн\\w*"),
            BeachActivity.DIVING, p("дайв\\w*|акваланг\\w*|понырять|\\bныря\\w*"),
            BeachActivity.SNORKELING, p("снорк\\w*|маск\\w* (?:и|с) трубк\\w*|с маской"));

    private static final Map<AccessNeed, Pattern> ACCESS_PATTERNS = linkedMap(
            AccessNeed.WHEELCHAIR, p("инвалидн\\w* коляск\\w*|на коляске|колясочник\\w*|кресл\\w*-?\\s?каталк\\w*"),
            AccessNeed.LIMITED_MOBILITY, p("плохо (?:хож\\w*|ход\\w*)|(?:трудно|тяжело) (?:ходить|передвигаться)|с трость\\w*|ходунк\\w*"
                    + "|маломобильн\\w*|инвалид\\w*|ограниченн\\w* (?:возможност|подвижност|мобильност)\\w*|\\bовз\\b|без (?:ступен|лестниц)\\w*"),
            AccessNeed.LOW_VISION, p("слабовидящ\\w*|незрячи\\w*|плохо (?:вижу|видит\\w*)|слаб\\w* зрени\\w*|\\bслеп\\w*"),
            AccessNeed.SENIORS, p("пожил\\w*|бабушк\\w*|дедушк\\w*|пенсионер\\w*|преклонн\\w*"));

    private static final Map<PetKind, Pattern> PET_PATTERNS = linkedMap(
            PetKind.DOG, p("собак\\w*|собачк\\w*|\\bпс(?:ом|а|ы)\\b|\\bпес\\b|\\bщен\\w*|\\bпсин\\w*"),
            PetKind.CAT, p("кошк\\w*|кошеч\\w*|\\bкот(?:ом|а|у|ик\\w*|ят\\w*|енк\\w*)?\\b"),
            PetKind.SMALL_PET, p("хорьк\\w*|хорек\\w*|крыс\\w*|хомяк\\w*|хомячк\\w*|морск\\w* свинк\\w*|кролик\\w*|шиншилл\\w*|грызун\\w*"),
            PetKind.BIRD, p("попуга\\w*|канарейк\\w*|\\bптиц\\w*"),
            PetKind.REPTILE, p("\\bс(?:о свое\\w*)? черепахой\\b|черепашк\\w*|ящериц\\w*|игуан\\w*|рептили\\w*|змеей\\b|\\bзмея\\b"),
            PetKind.OTHER, p("питомц\\w*|питомец|\\bс животн\\w*|домашн\\w* животн\\w*|зверюшк\\w*|зверьк\\w*|pet[- ]?friendly"));

    private static final Map<RoomFeature, Pattern> ROOM_PATTERNS = linkedMap(
            RoomFeature.BABY_COT, p("детск\\w* кроват\\w*|кроватк\\w*|манеж\\w*|люльк\\w*"),
            RoomFeature.EXTRA_BED, p("доп\\w*\\.?\\s?(?:кроват|мест)\\w*|раскладушк\\w*"),
            RoomFeature.WIFI_ROOM, p("(?:wi-?fi|вай-?фай|интернет\\w*) в номер\\w*"),
            RoomFeature.AIR_CON, p("кондиционер\\w*|кондей\\w*"),
            RoomFeature.KITCHEN, p("с кухн\\w*|кухн\\w* в номер\\w*|сво\\w* кухн\\w*|кухонн\\w*|мини-?кухн\\w*|готовить сам\\w*"),
            RoomFeature.BALCONY, p("балкон\\w*|лоджи\\w*"),
            RoomFeature.FRIDGE, p("холодильник\\w*"),
            RoomFeature.KETTLE, p("чайник\\w*"),
            RoomFeature.LAUNDRY, p("прачечн\\w*|стиральн\\w*|постирать|стирк\\w*"),
            RoomFeature.AIRPORT_SHUTTLE, p("трансфер\\w*|шаттл\\w*|встрет\\w* в аэропорт\\w*"),
            RoomFeature.RECEPTION_24H, p("круглосуточн\\w*|поздн\\w* заезд\\w*|ночн\\w* заезд\\w*|прилет\\w* ночью|ночью прилет\\w*|поздно прилет\\w*"));

    /** Разбирается после животных-питомцев: «с черепахой» — питомец, «посмотреть на черепах» — дикая природа. */
    private static final Map<Wildlife, Pattern> WILDLIFE_PATTERNS = linkedMap(
            Wildlife.DOLPHINS, p("дельфин\\w*"),
            Wildlife.SEA_TURTLES, p("черепах\\w*"),
            Wildlife.WHALES, p("\\bкит(?:ов|ы|ами|а)\\b|горбат\\w* кит\\w*|косатк\\w*"),
            Wildlife.MANTA_RAYS, p("\\bскат(?:ы|ов|ами|ом|а)?\\b|\\bмант(?:ы|а|ами|ой)?\\b"));

    /** Разбирается после «первой линии»: «у моря» и «на пляже» — более сильное требование. */
    private static final Pattern SEA = p("\\bна мор(?:е|я)\\b|\\bк морю\\b|\\bморе\\b|\\bморя\\b|\\bокеан\\w*|\\bпляжн\\w* отдых\\w*");

    private static final Pattern VISA_FREE = p("без\\s?виз\\w*|безвиз\\w*|не нужна виза|виза не нужна|без оформления виз\\w*");

    private static final Map<LocationFeature, Pattern> LOCATION_PATTERNS = linkedMap(
            LocationFeature.FIRST_LINE, p("перв\\w* линии?|\\b1(?:-?й)? линии?|на берегу|у (?:самого )?моря|на пляже"),
            LocationFeature.AWAY_FROM_TRAFFIC, p("(?:вдали|подальше|далеко) от (?:(?:оживленн|шумн|больш|загруженн|автомобильн)\\w* )?(?:дорог|трасс|магистрал|шоссе)\\w*"
                    + "|без шума (?:машин|дорог|трафик)\\w*|не у (?:дороги|трассы)|без мотобайк\\w*"),
            LocationFeature.CAR_ACCESS, p("подъехать на машин\\w*|подъезд\\w* (?:к отелю|на машин\\w*|до двери)|на (?:своей )?машин\\w*"
                    + "|на авто\\b|до двери|с чемоданами"),
            LocationFeature.SHOPPING, p("шо(?:п)?пинг\\w*|торгов\\w* центр\\w*|\\bтр?ц\\b|молл\\w*|гипермаркет\\w*|аутлет\\w*"
                    + "|за покупками|икеа|ikea|магазин\\w*"),
            LocationFeature.CITY_CENTER, p("\\bв центре\\b|\\bцентр(?:е|а)?\\b"));

    private final Clock clock;

    public RuleBasedIntentExtractor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExtractionResult extract(String query) {
        RuleAnalysis analysis = analyze(query);
        return new ExtractionResult(analysis.intent(), IntentSource.RULES,
                analysis.assumptions().stream().map(Note::text).toList(),
                analysis.clarifications().stream().map(Note::text).toList());
    }

    /**
     * Разбор с метаданными о происхождении каждого поля — нужен для слияния с ответом LLM.
     */
    public RuleAnalysis analyze(String query) {
        Objects.requireNonNull(query, "query");
        LocalDate today = LocalDate.now(clock);
        WorkText text = new WorkText(RussianText.normalize(query));
        Ctx ctx = new Ctx();

        TransportPreference transport = ctx.track(IntentField.TRANSPORT, parseTransport(text));
        Destination destination = ctx.track(IntentField.DESTINATION, DestinationDictionary.find(text.value())
                .map(match -> {
                    text.consume(match.start(), match.end());
                    if (match.hasExtra()) {
                        text.consume(match.extraStart(), match.extraEnd());
                    }
                    return match.destination();
                })
                .orElse(null));

        List<TransitConstraint> transit = parseTransit(text);
        Set<LocationFeature> locationFeatures = EnumSet.noneOf(LocationFeature.class);
        Matcher food = FOOD_NEARBY.matcher(text.value());
        if (food.find()) {
            locationFeatures.add(LocationFeature.FOOD_NEARBY);
            text.consume(food.start(), food.end());
        }
        List<ProximityConstraint> proximity = parseProximity(text, locationFeatures);
        Budget budget = ctx.track(IntentField.BUDGET, parseBudget(text, ctx));
        Integer minStars = ctx.track(IntentField.STARS, parseStars(text));
        StayPeriod stay = parseStay(text, today, ctx);
        Guests guests = parseGuests(text, ctx);

        MealPlan mealPlan = ctx.track(IntentField.MEAL_PLAN, firstMatch(text, MEAL_PATTERNS).orElse(null));
        Set<DietNeed> diet = wantedMatches(text, DIET_PATTERNS, DietNeed.class);
        if (diet.contains(DietNeed.VEGAN) || diet.contains(DietNeed.HINDU)) {
            diet.remove(DietNeed.VEGETARIAN);
        }
        Set<AccessNeed> access = allMatches(text, ACCESS_PATTERNS, AccessNeed.class);
        Set<PetKind> pets = allMatches(text, PET_PATTERNS, PetKind.class);
        if (pets.size() > 1) {
            pets.remove(PetKind.OTHER);
        }
        Set<RoomFeature> room = allMatches(text, ROOM_PATTERNS, RoomFeature.class);
        Set<Wildlife> wildlife = allMatches(text, WILDLIFE_PATTERNS, Wildlife.class);
        Matcher visa = VISA_FREE.matcher(text.value());
        boolean visaFree = visa.find();
        if (visaFree) {
            text.consume(visa.start(), visa.end());
        }
        Set<BeachActivity> activities = allMatches(text, ACTIVITY_PATTERNS, BeachActivity.class);
        Set<BeachPreference> beach = allMatches(text, BEACH_PATTERNS, BeachPreference.class);
        boolean ownCar = OWN_CAR.matcher(text.value()).find();
        Set<Amenity> amenities = wantedMatches(text, AMENITY_PATTERNS, Amenity.class);
        if (ownCar && !amenities.contains(Amenity.CAR_RENTAL)) {
            amenities.add(Amenity.PARKING);
        }
        Set<SoftPreference> soft = allMatches(text, SOFT_PATTERNS, SoftPreference.class);
        locationFeatures.addAll(allMatches(text, LOCATION_PATTERNS, LocationFeature.class));
        Matcher sea = SEA.matcher(text.value());
        if (sea.find() && !locationFeatures.contains(LocationFeature.FIRST_LINE)) {
            beach.add(BeachPreference.SEA);
            text.consume(sea.start(), sea.end());
        }

        if (destination == null) {
            Matcher unknown = UNKNOWN_PLACE.matcher(query);
            String place = null;
            while (unknown.find() && place == null) {
                String word = unknown.group(1);
                if (!MONTH.matcher(word.toLowerCase(java.util.Locale.ROOT)).find()) {
                    place = word;
                }
            }
            ctx.clarify(IntentField.DESTINATION, place != null
                    ? "«" + place + "» пока нет в нашем каталоге направлений. Попробуйте ближайший крупный город или регион."
                    : "Куда хотите поехать? Можно описать атмосферу — например, «тёплое море и горы».");
        }
        if (stay.month() == null) {
            ctx.clarify(IntentField.MONTH, "Когда планируете поездку? От сезона сильно зависит цена.");
        }

        SearchIntent intent = new SearchIntent(destination, stay, guests, budget, minStars, mealPlan,
                amenities, soft, locationFeatures, proximity, transport, beach, activities, access, room, pets, visaFree, wildlife,
                diet, transit);
        return new RuleAnalysis(intent, ctx.explicit, ctx.assumptions, ctx.clarifications);
    }

    // ---------------------------------------------------------------- transport

    private static TransportPreference parseTransport(WorkText text) {
        String origin = null;
        Matcher m = ORIGIN.matcher(text.value());
        if (m.find()) {
            origin = DestinationDictionary.find(m.group(1))
                    .map(match -> match.destination().displayName())
                    .orElse(null);
            text.consume(m.start(), m.end());
        }
        boolean direct = DIRECT_FLIGHT.matcher(text.value()).find();
        return origin == null && !direct ? null : new TransportPreference(origin, direct);
    }

    // ---------------------------------------------------------------- transit

    /** Аэропорт, вокзал, метро: «до аэропорта не больше 40 минут», «рядом с метро», «короткий трансфер». */
    private static List<TransitConstraint> parseTransit(WorkText text) {
        Map<TransitKind, Integer> limits = new EnumMap<>(TransitKind.class);
        Matcher m = TRANSIT_MAX.matcher(text.value());
        while (m.find()) {
            Integer minutes = minutes(m.group(2), m.group(3));
            if (minutes != null) {
                limits.merge(transitKind(m.group(1)), minutes, Math::min);
                text.consume(m.start(), m.end());
            }
        }
        m = TRANSIT_MINUTES_FIRST.matcher(text.value());
        while (m.find()) {
            Integer minutes = minutes(m.group(1), m.group(2));
            if (minutes != null) {
                limits.merge(transitKind(m.group(3)), minutes, Math::min);
                text.consume(m.start(), m.end());
            }
        }
        m = TRANSIT_NEAR.matcher(text.value());
        while (m.find()) {
            TransitKind kind = transitKind(m.group(1) != null ? m.group(1) : m.group(2));
            limits.putIfAbsent(kind, switch (kind) {
                case AIRPORT -> 40;
                case METRO -> 10;
                case RAIL, BUS -> 15;
            });
            text.consume(m.start(), m.end());
        }
        m = SHORT_TRANSFER.matcher(text.value());
        if (m.find()) {
            Integer minutes = m.group(1) != null ? minutes(m.group(1), m.group(2)) : null;
            limits.merge(TransitKind.AIRPORT, minutes != null ? minutes : 45, Math::min);
            text.consume(m.start(), m.end());
        }
        List<TransitConstraint> result = new ArrayList<>();
        limits.forEach((kind, minutes) -> result.add(new TransitConstraint(kind, Math.max(1, Math.min(600, minutes)))));
        return result;
    }

    private static TransitKind transitKind(String word) {
        String w = word.strip();
        if (w.startsWith("аэропорт")) {
            return TransitKind.AIRPORT;
        }
        if (w.startsWith("автовокзал") || w.startsWith("автостанци")) {
            return TransitKind.BUS;
        }
        return w.endsWith("метро") ? TransitKind.METRO : TransitKind.RAIL;
    }

    /** «40 минут», «1,5 часа», «полчаса», «два часа»; число без единиц до 5 — часы, больше — минуты. */
    static Integer minutes(String amount, String unit) {
        String a = amount.strip().replace(',', '.');
        double value = switch (a) {
            case "полчаса" -> 0.5;
            case "полтора" -> 1.5;
            case "час" -> 1;
            case "два" -> 2;
            case "три" -> 3;
            case "четыре" -> 4;
            case "пять" -> 5;
            default -> Double.parseDouble(a);
        };
        boolean hours;
        if (unit != null) {
            hours = unit.startsWith("ч");
        } else {
            hours = switch (a) {
                case "полчаса", "полтора", "час" -> true;
                default -> value <= 5;
            };
        }
        int result = (int) Math.round(hours ? value * 60 : value);
        return result >= 1 && result <= 600 ? result : null;
    }

    // ---------------------------------------------------------------- proximity

    private static List<ProximityConstraint> parseProximity(WorkText text, Set<LocationFeature> features) {
        List<ProximityConstraint> result = new ArrayList<>();
        Matcher m = PROXIMITY_TO.matcher(text.value());
        while (m.find()) {
            int minutes = m.group(2) != null ? Integer.parseInt(m.group(2)) : DEFAULT_WALK_MINUTES;
            addProximity(m.group(1), minutes, result, features);
            text.consume(m.start(), m.end());
        }
        m = PROXIMITY_MINUTES_FIRST.matcher(text.value());
        while (m.find()) {
            addProximity(m.group(2), Integer.parseInt(m.group(1)), result, features);
            text.consume(m.start(), m.end());
        }
        m = PROXIMITY_NEAR.matcher(text.value());
        while (m.find()) {
            addProximity(m.group(1), 10, result, features);
            text.consume(m.start(), m.end());
        }
        m = PROXIMITY_WANT.matcher(text.value());
        while (m.find()) {
            if (!SEA_OR_BEACH.matcher(m.group(1)).find()) {
                addProximity(m.group(1), DEFAULT_WALK_MINUTES, result, features);
                text.consume(m.start(), m.end());
            }
        }
        return result;
    }

    private static void addProximity(String word, int minutes, List<ProximityConstraint> sink,
                                     Set<LocationFeature> features) {
        if (SEA_OR_BEACH.matcher(word).find()) {
            features.add(LocationFeature.FIRST_LINE);
        } else if (CENTER_WORD.matcher(word).find()) {
            features.add(LocationFeature.CITY_CENTER);
        } else if (minutes >= 1 && minutes <= 120) {
            sink.add(new ProximityConstraint(word, minutes));
        }
    }

    // ---------------------------------------------------------------- budget

    private static Budget parseBudget(WorkText text, Ctx ctx) {
        for (Pattern pattern : List.of(BUDGET, BUDGET_SHORT)) {
            Matcher m = pattern.matcher(text.value());
            while (m.find()) {
                String tail = text.value().substring(m.end());
                if (NOT_MONEY_UNIT.matcher(tail).find()) {
                    continue;
                }
                Optional<BigDecimal> amount = parseMoney(m.group(1), m.group(2), ctx);
                if (amount.isEmpty()) {
                    continue;
                }
                Budget.Scope scope = PER_NIGHT.matcher(tail).find() ? Budget.Scope.PER_NIGHT : Budget.Scope.TOTAL;
                text.consume(m.start(), m.end());
                return new Budget(amount.get(), scope);
            }
        }
        return null;
    }

    private static Optional<BigDecimal> parseMoney(String digits, String unit, Ctx ctx) {
        String cleaned = digits.replace(" ", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(cleaned);
        if (unit != null) {
            BigDecimal multiplier = unit.startsWith("млн") ? BigDecimal.valueOf(1_000_000) : BigDecimal.valueOf(1_000);
            value = value.multiply(multiplier);
        } else if (value.compareTo(BigDecimal.valueOf(500)) < 0) {
            // «до 80» в контексте поездки почти всегда означает тысячи
            value = value.multiply(BigDecimal.valueOf(1_000));
            ctx.assume(IntentField.BUDGET,
                    "Бюджет «" + digits.strip() + "» поняли как " + value.toPlainString() + " ₽");
        }
        value = value.setScale(0, RoundingMode.HALF_UP);
        return value.signum() > 0 ? Optional.of(value) : Optional.empty();
    }

    // ---------------------------------------------------------------- stars

    private static Integer parseStars(WorkText text) {
        Matcher m = STARS_DIGIT.matcher(text.value());
        if (m.find()) {
            text.consume(m.start(), m.end());
            return Integer.parseInt(m.group(1));
        }
        m = STARS_WORD.matcher(text.value());
        if (m.find()) {
            text.consume(m.start(), m.end());
            String w = m.group(1);
            return w.startsWith("пят") ? 5 : w.startsWith("четыр") ? 4 : 3;
        }
        return null;
    }

    // ---------------------------------------------------------------- stay

    private static StayPeriod parseStay(WorkText text, LocalDate today, Ctx ctx) {
        Matcher range = DATE_RANGE.matcher(text.value());
        if (range.find()) {
            int month = monthNumber(range.group(3));
            YearMonth ym = nearestFuture(month, today);
            int fromDay = Integer.parseInt(range.group(1));
            int toDay = Integer.parseInt(range.group(2));
            if (fromDay >= 1 && toDay > fromDay && toDay <= ym.lengthOfMonth()) {
                text.consume(range.start(), range.end());
                ctx.explicit.addAll(List.of(IntentField.MONTH, IntentField.CHECK_IN, IntentField.NIGHTS));
                return new StayPeriod(ym, ym.atDay(fromDay), toDay - fromDay);
            }
        }

        YearMonth month = null;
        Matcher m = MONTH.matcher(text.value());
        if (m.find()) {
            month = nearestFuture(monthNumber(m.group(1)), today);
            text.consume(m.start(), m.end());
            ctx.explicit.add(IntentField.MONTH);
        }

        boolean weekend = WEEKEND.matcher(text.value()).find();
        Integer nights = parseNights(text, ctx);
        if (nights != null) {
            ctx.explicit.add(IntentField.NIGHTS);
        } else {
            nights = weekend ? DEFAULT_WEEKEND_NIGHTS : DEFAULT_NIGHTS;
            ctx.assume(IntentField.NIGHTS, weekend
                    ? "Выходные — считаем 2 ночи (пятница–воскресенье)"
                    : "Длительность не указана — считаем " + DEFAULT_NIGHTS + " ночей");
        }

        LocalDate checkIn = null;
        if (weekend && month != null) {
            LocalDate firstDay = month.atDay(1).isBefore(today) ? today : month.atDay(1);
            checkIn = firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            if (!YearMonth.from(checkIn).equals(month)) {
                checkIn = null;
            }
        }
        if (checkIn != null) {
            ctx.explicit.add(IntentField.CHECK_IN);
        }
        if (month == null) {
            ctx.assume(IntentField.MONTH, "Даты не указаны — цены ориентировочные на ближайший месяц");
        }
        return new StayPeriod(month, checkIn, nights);
    }

    private static Integer parseNights(WorkText text, Ctx ctx) {
        Matcher m = NIGHTS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                return clampNights(n.getAsInt());
            }
        }
        m = DAYS_AND_NIGHTS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                return clampNights(n.getAsInt());
            }
        }
        m = DAYS.matcher(text.value());
        if (m.find()) {
            OptionalInt n = RussianText.parseNumber(m.group(1));
            if (n.isPresent()) {
                text.consume(m.start(), m.end());
                // «10 дней» в туризме — это 9 ночей
                int nights = clampNights(n.getAsInt() - 1);
                ctx.assume(IntentField.NIGHTS, n.getAsInt() + " дн. — это " + nights + " ноч. проживания");
                return nights;
            }
        }
        m = WEEKS.matcher(text.value());
        if (m.find()) {
            int weeks = RussianText.parseNumber(m.group(1)).orElse(1);
            text.consume(m.start(), m.end());
            return clampNights(weeks * 7);
        }
        return null;
    }

    private static int clampNights(int nights) {
        return Math.max(1, Math.min(60, nights));
    }

    private static int monthNumber(String word) {
        String w = word.toLowerCase(java.util.Locale.ROOT);
        if (w.startsWith("янв")) return 1;
        if (w.startsWith("фев")) return 2;
        if (w.startsWith("мар")) return 3;
        if (w.startsWith("апр")) return 4;
        if (w.startsWith("ма")) return 5;
        if (w.startsWith("июн")) return 6;
        if (w.startsWith("июл")) return 7;
        if (w.startsWith("авг")) return 8;
        if (w.startsWith("сен")) return 9;
        if (w.startsWith("окт")) return 10;
        if (w.startsWith("ноя")) return 11;
        if (w.startsWith("дек")) return 12;
        throw new IllegalArgumentException("Unknown month: " + word);
    }

    private static YearMonth nearestFuture(int month, LocalDate today) {
        YearMonth candidate = YearMonth.of(today.getYear(), month);
        return candidate.isBefore(YearMonth.from(today)) ? candidate.plusYears(1) : candidate;
    }

    // ---------------------------------------------------------------- guests

    private static Guests parseGuests(WorkText text, Ctx ctx) {
        Integer adults = null;
        Matcher m = ADULTS.matcher(text.value());
        if (m.find()) {
            adults = RussianText.parseNumber(m.group(1)).stream().boxed().findFirst().orElse(null);
            text.consume(m.start(), m.end());
        } else if (SOLO.matcher(text.value()).find()) {
            adults = 1;
        } else if (COUPLE.matcher(text.value()).find()) {
            adults = 2;
        }
        Integer partySize = null;
        Matcher party = PARTY_SIZE.matcher(text.value());
        if (party.find()) {
            String token = party.group(1) != null ? party.group(1) : party.group(2);
            partySize = RussianText.parseNumber(token).stream().boxed().findFirst().orElse(null);
            text.consume(party.start(), party.end());
        }

        List<Integer> ages = new ArrayList<>();
        int unknownAges = 0;
        m = CHILDREN_WITH_AGES.matcher(text.value());
        while (m.find()) {
            List<Integer> found = new ArrayList<>();
            boolean months = m.group().contains("мес");
            for (String part : AGE_LIST_SPLIT.split(m.group(2).strip())) {
                int age = months ? 0 : Integer.parseInt(part.strip());
                if (age <= 17) {
                    found.add(age);
                }
            }
            int declared = RussianText.parseNumber(m.group(1)).orElse(found.size());
            ages.addAll(found);
            unknownAges += Math.max(0, declared - found.size());
            text.consume(m.start(), m.end());
        }
        m = CHILDREN_NO_AGE.matcher(text.value());
        while (m.find()) {
            unknownAges += RussianText.parseNumber(m.group(1)).orElse(1);
            text.consume(m.start(), m.end());
        }
        if (unknownAges > 0) {
            for (int i = 0; i < unknownAges; i++) {
                ages.add(UNKNOWN_CHILD_AGE);
            }
            ctx.clarify(IntentField.CHILDREN, "Сколько лет детям? От возраста зависят цена и размещение.");
        }
        if (!ages.isEmpty() && unknownAges == 0) {
            ctx.explicit.add(IntentField.CHILDREN);
        }

        if (adults == null && partySize != null) {
            adults = Math.max(1, partySize - ages.size());
        }
        if (adults != null) {
            ctx.explicit.add(IntentField.ADULTS);
        } else {
            adults = DEFAULT_ADULTS;
            ctx.assume(IntentField.ADULTS, "Число гостей не указано — считаем " + DEFAULT_ADULTS + " взрослых");
        }
        if (adults > 10) {
            ctx.assume(IntentField.ADULTS, "В одном номере не больше 10 взрослых — для компании из " + adults
                    + " человек забронируйте несколько номеров");
        }
        return new Guests(Math.max(1, Math.min(10, adults)), ages);
    }

    // ---------------------------------------------------------------- helpers

    private static <E> Optional<E> firstMatch(WorkText text, Map<E, Pattern> patterns) {
        for (Map.Entry<E, Pattern> e : patterns.entrySet()) {
            Matcher m = e.getValue().matcher(text.value());
            if (m.find()) {
                text.consume(m.start(), m.end());
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> Set<E> allMatches(WorkText text, Map<E, Pattern> patterns, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        for (Map.Entry<E, Pattern> e : patterns.entrySet()) {
            Matcher m = e.getValue().matcher(text.value());
            if (m.find()) {
                result.add(e.getKey());
                text.consume(m.start(), m.end());
            }
        }
        return result;
    }

    /** Как {@link #allMatches}, но «бар не нужен», «кошерное не обязательно», «не надо бассейн» не добавляются. */
    private static <E extends Enum<E>> Set<E> wantedMatches(WorkText text, Map<E, Pattern> patterns, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        for (Map.Entry<E, Pattern> e : patterns.entrySet()) {
            Matcher m = e.getValue().matcher(text.value());
            while (m.find()) {
                // смотрим в исходный текст: слова отрицания могли уже «съесть» другие правила
                String before = text.original().substring(Math.max(0, m.start() - 16), m.start());
                String after = text.original().substring(m.end(), Math.min(text.original().length(), m.end() + 30));
                text.consume(m.start(), m.end());
                if (NEGATED_BEFORE.matcher(before).find() || NEGATED_AFTER.matcher(after).find()) {
                    continue;
                }
                result.add(e.getKey());
                break;
            }
        }
        return result;
    }

    private static final Pattern NEGATED_BEFORE = p("(?:\\bне нуж\\w*|\\bне надо|\\bне важн\\w*)\\s*$");
    private static final Pattern NEGATED_AFTER = p("^\\s*(?:\\p{L}+\\s+)?(?:не нуж|не надо|не обязательн|не важн|ни к чему)");

    private static Pattern p(String regex) {
        return Pattern.compile(regex, FLAGS);
    }

    private static <K, V> Map<K, V> linkedMap(Object... keyValues) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked") K key = (K) keyValues[i];
            @SuppressWarnings("unchecked") V value = (V) keyValues[i + 1];
            map.put(key, value);
        }
        return map;
    }

    /** Накопитель метаданных разбора. */
    private static final class Ctx {
        private final Set<IntentField> explicit = EnumSet.noneOf(IntentField.class);
        private final List<Note> assumptions = new ArrayList<>();
        private final List<Note> clarifications = new ArrayList<>();

        <T> T track(IntentField field, T value) {
            if (value != null) {
                explicit.add(field);
            }
            return value;
        }

        void assume(IntentField field, String text) {
            assumptions.add(new Note(field, text));
        }

        void clarify(IntentField field, String text) {
            clarifications.add(new Note(field, text));
        }
    }

    /**
     * Рабочая копия текста: распознанные фрагменты заменяются пробелами той же длины,
     * поэтому индексы остаются стабильными.
     */
    private static final class WorkText {
        private final StringBuilder buffer;
        private final String original;

        WorkText(String text) {
            this.buffer = new StringBuilder(text);
            this.original = text;
        }

        /** Текст до вычёркиваний — той же длины, позиции совпадают. */
        String original() {
            return original;
        }

        String value() {
            return buffer.toString();
        }

        void consume(int start, int end) {
            for (int i = start; i < end; i++) {
                buffer.setCharAt(i, ' ');
            }
        }
    }
}
