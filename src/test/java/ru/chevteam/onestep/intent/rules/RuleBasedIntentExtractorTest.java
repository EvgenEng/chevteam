package ru.chevteam.onestep.intent.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TransitKind;
import ru.chevteam.onestep.intent.TravelStyle;
import ru.chevteam.onestep.intent.Wildlife;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedIntentExtractorTest {

    private static final Clock TODAY_2026_10_24 =
            Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneId.of("Europe/Moscow"));

    private final RuleBasedIntentExtractor extractor = new RuleBasedIntentExtractor(TODAY_2026_10_24);

    @Nested
    @DisplayName("Демо-сценарии")
    class DemoScenarios {

        @Test
        @DisplayName("Турция: семья, всё включено, аквапарк, бюджет на всю поездку")
        void turkeyFamily() {
            SearchIntent intent = extract("Хочу в Турцию на 10 дней в мае, всё включено, отель с аквапарком, "
                    + "двое взрослых и ребёнок 7 лет, до 80 тысяч. Лечу из Москвы, можно с пересадкой но лучше прямой.");

            assertThat(intent.destination()).isEqualTo(Destination.country("Турция"));
            assertThat(intent.stay().month()).isEqualTo(YearMonth.of(2027, 5));
            assertThat(intent.stay().nights()).isEqualTo(9);
            assertThat(intent.guests().adults()).isEqualTo(2);
            assertThat(intent.guests().childrenAges()).containsExactly(7);
            assertThat(intent.travelStyle()).isEqualTo(TravelStyle.FAMILY);
            assertThat(intent.budget()).isEqualTo(Budget.total(80_000));
            assertThat(intent.mealPlan()).isEqualTo(MealPlan.ALL_INCLUSIVE);
            assertThat(intent.amenities()).containsExactly(Amenity.AQUAPARK);
            assertThat(intent.transport().originCity()).isEqualTo("Москва");
            assertThat(intent.transport().directPreferred()).isTrue();
        }

        @Test
        @DisplayName("Сочи: соло, 4★, первая линия, завтрак")
        void sochiSolo() {
            SearchIntent intent = extract("Ищу отель в Сочи на 7 дней в сентябре, 4 звезды, на первой линии, "
                    + "с завтраком, бюджет до 50 тысяч. Один еду.");

            assertThat(intent.destination()).isEqualTo(Destination.city("Россия", "Краснодарский край", "Сочи"));
            assertThat(intent.stay().month()).isEqualTo(YearMonth.of(2027, 9));
            assertThat(intent.stay().nights()).isEqualTo(6);
            assertThat(intent.guests().adults()).isEqualTo(1);
            assertThat(intent.minStars()).isEqualTo(4);
            assertThat(intent.locationFeatures()).containsExactly(LocationFeature.FIRST_LINE);
            assertThat(intent.mealPlan()).isEqualTo(MealPlan.BREAKFAST);
            assertThat(intent.budget()).isEqualTo(Budget.total(50_000));
        }

        @Test
        @DisplayName("Питер: выходные, пешком до Эрмитажа, мягкие пожелания")
        void spbWeekend() {
            SearchIntent intent = extract("Куда сходить в Питере на 2 ночи в выходные в январе? Центр, "
                    + "до Эрмитажа пешком минут 15, чисто, тихо, хороший Wi-Fi. До 8000 за двое суток.");

            assertThat(intent.destination()).isEqualTo(Destination.city("Россия", "Санкт-Петербург", "Санкт-Петербург"));
            assertThat(intent.stay().nights()).isEqualTo(2);
            assertThat(intent.stay().checkIn()).isEqualTo(LocalDate.of(2027, 1, 1)); // первая пятница января
            assertThat(intent.proximity()).containsExactly(new ProximityConstraint("эрмитажа", 15));
            assertThat(intent.locationFeatures()).containsExactly(LocationFeature.CITY_CENTER);
            assertThat(intent.softPreferences())
                    .containsExactlyInAnyOrder(SoftPreference.QUIET, SoftPreference.CLEAN, SoftPreference.GOOD_WIFI);
            assertThat(intent.budget()).isEqualTo(Budget.total(8_000));
        }
    }

    @Nested
    @DisplayName("Бюджет")
    class BudgetParsing {

        @ParameterizedTest(name = "«{0}» → {1} ({2})")
        @CsvSource(delimiter = '|', value = {
                "Сочи до 50 тысяч            | 50000  | TOTAL",
                "Сочи, бюджет 120к           | 120000 | TOTAL",
                "Сочи до 80 000 ₽            | 80000  | TOTAL",
                "Сочи до 5000 за ночь        | 5000   | PER_NIGHT",
                "Сочи не дороже 6 тыс в сутки | 6000  | PER_NIGHT",
        })
        void parsesBudget(String query, long amount, Budget.Scope scope) {
            assertThat(extract(query).budget()).isEqualTo(new Budget(BigDecimal.valueOf(amount), scope));
        }

        @Test
        @DisplayName("минуты, звёзды и дни не принимаются за деньги")
        void doesNotConfuseNumbers() {
            SearchIntent intent = extract("Казань, до Кремля пешком до 15 минут, 4 звезды, на 5 дней");
            assertThat(intent.budget()).isNull();
            assertThat(intent.minStars()).isEqualTo(4);
            assertThat(intent.stay().nights()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Даты и гости")
    class StayAndGuests {

        @Test
        void dateRangeGivesExactCheckIn() {
            SearchIntent intent = extract("Казань с 10 по 14 июня");
            assertThat(intent.stay().checkIn()).isEqualTo(LocalDate.of(2027, 6, 10));
            assertThat(intent.stay().nights()).isEqualTo(4);
        }

        @Test
        void currentMonthStaysInCurrentYear() {
            assertThat(extract("Сочи в октябре").stay().month()).isEqualTo(YearMonth.of(2026, 10));
        }

        @Test
        void partySizeMinusChildren() {
            SearchIntent intent = extract("Сочи, нас трое, малышке 4 годика");
            assertThat(intent.guests().adults()).isEqualTo(2);
            assertThat(intent.guests().childrenAges()).containsExactly(4);
        }

        @Test
        void childrenWithoutAgesTriggerClarification() {
            ExtractionResult result = extractor.extract("Турция, двое детей, всё включено");
            assertThat(result.intent().guests().children()).isEqualTo(2);
            assertThat(result.clarifications()).anyMatch(c -> c.contains("Сколько лет детям"));
        }

        @Test
        void defaultsAreReportedAsAssumptions() {
            ExtractionResult result = extractor.extract("Сочи у моря");
            assertThat(result.intent().guests().adults()).isEqualTo(2);
            assertThat(result.intent().stay().nights()).isEqualTo(7);
            assertThat(result.assumptions()).hasSize(3);
        }

        @Test
        void detailedAnalysisMarksExplicitFields() {
            RuleAnalysis analysis = extractor.analyze("Сочи, 2 взрослых, 5 ночей");
            assertThat(analysis.explicit())
                    .contains(IntentField.DESTINATION, IntentField.ADULTS, IntentField.NIGHTS)
                    .doesNotContain(IntentField.MONTH, IntentField.BUDGET);
        }
    }

    @ParameterizedTest(name = "«{0}» → {1} / {2}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "Хочу в Тайланд на 7 дней       | Таиланд  | -",
            "в Таиланд, на Пхукет           | Таиланд  | Пхукет",
            "Дубай в декабре                | ОАЭ      | Дубай",
            "Египет, всё включено           | Египет   | -",
            "Хургада с детьми               | Египет   | Хургада",
            "на Мальдивы вдвоём             | Мальдивы | -",
            "Грузия, Батуми, море           | Грузия   | Батуми",
            "Китай, хочу в Пекин            | Китай    | Пекин",
            "на Хайнань зимой               | Китай    | Санья",
            "Япония в апреле, сакура        | Япония   | -",
            "Токио на неделю                | Япония   | Токио",
            "в Южную Корею                  | Южная Корея | -",
            "Сеул и Пусан                   | Южная Корея | Сеул",
            "Камбоджа, Ангкор               | Камбоджа | Сием-Рип",
            "в Сием-Рип к храмам            | Камбоджа | Сием-Рип",
            "Лаос, Луангпрабанг             | Лаос     | Луангпрабанг",
            "Сингапур с детьми              | Сингапур | Сингапур",
            "Малайзия, Лангкави             | Малайзия | Лангкави",
            "Вьетнам, Фукуок                | Вьетнам  | Фукуок",
            "Ханой и Халонг                 | Вьетнам  | Ханой",
            "на Бали в июле                 | Индонезия | Бали",
            "Индия, Гоа                     | Индия    | Гоа",
            "Боракай, Филиппины             | Филиппины | Боракай",
            "Стамбул на выходные            | Турция   | Стамбул",
    })
    void recognizesPopularOutboundDestinations(String query, String country, String city) {
        Destination destination = extract(query).destination();
        assertThat(destination.country()).isEqualTo(country);
        assertThat(destination.city()).isEqualTo(city);
    }

    @Test
    void countryAndCityTogetherKeepTextBetweenThem() {
        SearchIntent intent = extract("Таиланд в мае, Пхукет");
        assertThat(intent.destination()).isEqualTo(Destination.city("Таиланд", "Пхукет"));
        assertThat(intent.stay().month()).isEqualTo(YearMonth.of(2027, 5));
    }

    @Test
    void multiWordLandmark() {
        SearchIntent intent = extract("Сингапур, до Марина Бэй Сэндс пешком 10 минут");
        assertThat(intent.proximity()).containsExactly(new ProximityConstraint("марина бэй сэндс", 10));
    }

    @Test
    void cuisineAdjectiveIsNotADestination() {
        assertThat(extract("Сочи, рядом китайский ресторан").destination()).isEqualTo(Destination.city("Россия", "Краснодарский край", "Сочи"));
    }

    @Test
    void thailandQueryFromUser() {
        SearchIntent intent = extract("Хочу в Тайланд на 7 дней, двое взрослых, до 100 тысяч. "
                + "Лечу из Москвы, можно с пересадкой но лучше прямой.");
        assertThat(intent.destination()).isEqualTo(Destination.country("Таиланд"));
        assertThat(intent.stay().nights()).isEqualTo(6);
        assertThat(intent.guests().adults()).isEqualTo(2);
        assertThat(intent.budget()).isEqualTo(Budget.total(100_000));
        assertThat(intent.transport().originCity()).isEqualTo("Москва");
    }

    @Test
    void unknownDestinationAsksWhereToGo() {
        ExtractionResult result = extractor.extract("хочу тёплое море и горы");
        assertThat(result.intent().destination()).isNull();
        assertThat(result.clarifications()).first().asString().startsWith("Куда хотите поехать");
    }

    @Test
    void emptySetsAreNeverNull() {
        SearchIntent intent = extract("Сочи");
        assertThat(intent.amenities()).isEqualTo(Set.of());
        assertThat(intent.proximity()).isEqualTo(List.of());
    }

    @ParameterizedTest(name = "«{0}» → {1} / {2} / {3}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "Хочу в Крым летом                          | Россия      | Крым                    | -",
            "Крым, Ялта, первая линия                   | Россия      | Крым                    | Ялта",
            "в Евпаторию с ребёнком                     | Россия      | Крым                    | Евпатория",
            "отдых по России на море                    | Россия      | -                       | -",
            "Россия, Карелия, июль                      | Россия      | Карелия                 | -",
            "Золотое кольцо на выходные                 | Россия      | Золотое кольцо          | -",
            "Суздаль на выходные                        | Россия      | Золотое кольцо          | Суздаль",
            "Калининградская область, Балтика           | Россия      | Калининградская область | -",
            "Зеленоградск, кайт                         | Россия      | Калининградская область | Зеленоградск",
            "Выборг на 2 ночи                           | Россия      | Ленинградская область   | Выборг",
            "Кронштадт и форты                          | Россия      | Санкт-Петербург         | Кронштадт",
            "Великий Новгород, кремль                   | Россия      | Новгородская область    | Великий Новгород",
            "Алтай, Чемал                               | Россия      | Алтай                   | Чемал",
            "на Байкал зимой                            | Россия      | Байкал                  | -",
            "Дагестан, Дербент                          | Россия      | Дагестан                | Дербент",
            "Брест и Беловежская пуща                   | Беларусь    | -                       | Брест",
            "Гродно, старый город                       | Беларусь    | -                       | Гродно",
            "в Белоруссию на майские                    | Беларусь    | -                       | -",
            "Алматы и Медеу                             | Казахстан   | -                       | Алматы",
            "Самарканд и Бухара                         | Узбекистан  | -                       | Самарканд",
            "Армения, Ереван                            | Армения     | -                       | Ереван",
            "Баку на выходные                           | Азербайджан | -                       | Баку",
            "Иссык-Куль с детьми                        | Кыргызстан  | -                       | Чолпон-Ата",
            "Австралия, Сидней                          | Австралия   | -                       | Сидней",
            "хочу на Большой Барьерный риф              | Австралия   | -                       | Кэрнс",
            "Новая Зеландия, Куинстаун                  | Новая Зеландия | -                    | Куинстаун",
            "Чэнду и база панд                          | Китай       | Сычуань                 | Чэнду",
            "Сычуань в апреле                           | Китай       | Сычуань                 | -",
            "хочу к Терракотовой армии                  | Китай       | Шэньси                  | Сиань",
            "горы Аватара, Чжанцзяцзе                   | Китай       | Хунань                  | Чжанцзяцзе",
            "Юньнань, Лицзян                            | Китай       | Юньнань                 | Лицзян",
            "Харбин на фестиваль льда                   | Китай       | Хэйлунцзян              | Харбин",
            "Дагестан, Махачкала и Сарыкум              | Россия      | Дагестан                | Махачкала",
            "Екатеринбург на выходные                   | Россия      | Свердловская область    | Екатеринбург",
            "Крым, Коктебель                            | Россия      | Крым                    | Коктебель",
    })
    void recognizesRegionsAndNewCountries(String query, String country, String region, String city) {
        Destination destination = extract(query).destination();
        assertThat(destination).isNotNull();
        assertThat(destination.country()).isEqualTo(country);
        assertThat(destination.region()).isEqualTo(region);
        assertThat(destination.city()).isEqualTo(city);
    }

    @Nested
    @DisplayName("Пляж, активности и доступная среда")
    class BeachAndAccess {

        @Test
        void beachPreferences() {
            SearchIntent intent = extract("Бали с ребёнком 4 лет, песчаный пляж, мелководье, без сильных отливов и без медуз, "
                    + "немноголюдно, чтобы были банан и парасейлинг");
            assertThat(intent.beach()).containsExactlyInAnyOrder(BeachPreference.SANDY, BeachPreference.GENTLE_ENTRY,
                    BeachPreference.NO_TIDES, BeachPreference.NO_JELLYFISH, BeachPreference.UNCROWDED,
                    BeachPreference.BEACH_FUN);
            assertThat(intent.wantsBeach()).isTrue();
        }

        @Test
        void kitesurfingIsNotSurfing() {
            SearchIntent intent = extract("Хургада, кайтсерфинг и дайвинг, ноябрь");
            assertThat(intent.activities()).containsExactlyInAnyOrder(BeachActivity.KITESURFING, BeachActivity.DIVING);
        }

        @Test
        void surfingAndSnorkeling() {
            SearchIntent intent = extract("Бали, хочу научиться серфингу и поплавать с маской");
            assertThat(intent.activities()).containsExactlyInAnyOrder(BeachActivity.SURFING, BeachActivity.SNORKELING);
        }

        @Test
        void wheelchairAndSeniors() {
            SearchIntent intent = extract("Сочи с пожилыми родителями, папа на инвалидной коляске");
            assertThat(intent.access()).containsExactlyInAnyOrder(AccessNeed.WHEELCHAIR, AccessNeed.SENIORS);
            assertThat(intent.needsSlowPace()).isTrue();
        }

        @Test
        void limitedMobilityAndLowVision() {
            SearchIntent intent = extract("Питер, мама плохо ходит и слабовидящая, без лестниц");
            assertThat(intent.access()).containsExactlyInAnyOrder(AccessNeed.LIMITED_MOBILITY, AccessNeed.LOW_VISION);
        }

        @Test
        void petsAreRecognized() {
            assertThat(extract("Москва с собакой").pets()).containsExactly(PetKind.DOG);
            assertThat(extract("Питер, едем с котом и хорьком").pets()).containsExactlyInAnyOrder(PetKind.CAT, PetKind.SMALL_PET);
            assertThat(extract("Казань с черепахой и попугаем").pets()).containsExactlyInAnyOrder(PetKind.REPTILE, PetKind.BIRD);
            assertThat(extract("Сочи с питомцем").pets()).containsExactly(PetKind.OTHER);
            assertThat(extract("Сочи, отель который у моря").pets()).isEmpty();
        }

        @Test
        void roomFeaturesAreRecognized() {
            SearchIntent intent = extract("Питер, нужна детская кроватка, кондиционер и кухня в номере, ночью прилетаем");
            assertThat(intent.room()).containsExactlyInAnyOrder(RoomFeature.BABY_COT, RoomFeature.AIR_CON,
                    RoomFeature.KITCHEN, RoomFeature.RECEPTION_24H);
        }

        @Test
        void localCuisineIsNotAKitchen() {
            assertThat(extract("Грузия, Тбилиси, вкусная кухня").room()).isEmpty();
        }

        @Test
        void visaFreeAndSea() {
            SearchIntent intent = extract("Куда без визы на море в декабре");
            assertThat(intent.visaFreeOnly()).isTrue();
            assertThat(intent.beach()).contains(BeachPreference.SEA);
            assertThat(intent.wantsBeach()).isTrue();
            assertThat(extract("Сочи у моря").beach()).doesNotContain(BeachPreference.SEA);
        }

        @Test
        void wildlifeVersusPetTurtle() {
            SearchIntent swim = extract("Бали, поплавать с черепахами и скатами, увидеть дельфинов");
            assertThat(swim.wildlife()).containsExactlyInAnyOrder(Wildlife.SEA_TURTLES, Wildlife.MANTA_RAYS, Wildlife.DOLPHINS);
            assertThat(swim.pets()).isEmpty();
            SearchIntent pet = extract("Сочи, едем с черепахой");
            assertThat(pet.pets()).containsExactly(PetKind.REPTILE);
            assertThat(pet.wildlife()).isEmpty();
            assertThat(extract("Сидней, хочу увидеть китов").wildlife()).containsExactly(Wildlife.WHALES);
            assertThat(extract("Китай, Пекин").wildlife()).isEmpty();
        }

        @Test
        void roadsCarAndShopping() {
            SearchIntent intent = extract("Ханой, подальше от дороги, приедем на машине, торговый центр рядом");
            assertThat(intent.locationFeatures()).containsExactlyInAnyOrder(LocationFeature.AWAY_FROM_TRAFFIC,
                    LocationFeature.CAR_ACCESS, LocationFeature.SHOPPING);
            assertThat(extract("Москва в центре").locationFeatures()).containsExactly(LocationFeature.CITY_CENTER);
        }

        @Test
        void transitLimits() {
            SearchIntent intent = extract("Москва, до аэропорта не больше 40 минут, рядом с метро");
            assertThat(intent.transit()).containsExactlyInAnyOrder(new TransitConstraint(TransitKind.AIRPORT, 40),
                    new TransitConstraint(TransitKind.METRO, 10));
            assertThat(intent.proximity()).isEmpty();
            assertThat(extract("Сочи, от вокзала 2,5 часа максимум").transit())
                    .containsExactly(new TransitConstraint(TransitKind.RAIL, 150));
            assertThat(extract("Казань, 15 минут пешком до метро").transit())
                    .containsExactly(new TransitConstraint(TransitKind.METRO, 15));
            assertThat(extract("Ялта, до ж/д вокзала за полтора часа").transit())
                    .containsExactly(new TransitConstraint(TransitKind.RAIL, 90));
            assertThat(extract("Бали, короткий трансфер").transit())
                    .containsExactly(new TransitConstraint(TransitKind.AIRPORT, 45));
            assertThat(extract("Питер, до Эрмитажа пешком 10 минут").transit()).isEmpty();
        }

        @Test
        void dietNeeds() {
            assertThat(extract("Дубай, халяльная кухня").diet()).containsExactly(DietNeed.HALAL);
            assertThat(extract("Москва, кошерное питание и детское меню").diet())
                    .containsExactlyInAnyOrder(DietNeed.KOSHER, DietNeed.KIDS_MENU);
            assertThat(extract("Бали, веганская кухня").diet()).containsExactly(DietNeed.VEGAN);
            SearchIntent hindu = extract("Гоа, индийская вегетарианская кухня, без глютена");
            assertThat(hindu.diet()).containsExactlyInAnyOrder(DietNeed.HINDU, DietNeed.GLUTEN_FREE);
            assertThat(hindu.room()).isEmpty();
            assertThat(extract("Кисловодск, диетическое питание").diet()).containsExactly(DietNeed.DIETARY);
            assertThat(extract("Турция, с кухней в номере").diet()).isEmpty();
            assertThat(extract("Калининград, кошерное питание не нужно — просто вегетарианское").diet())
                    .containsExactly(DietNeed.VEGETARIAN);
            assertThat(extract("Сочи, бар не нужен, нужен бассейн").amenities()).containsExactly(Amenity.POOL);
        }

        @Test
        void cafesNearbyDoNotBecomeMealPlan() {
            SearchIntent intent = extract("Питер, рядом вкусные кафе для завтрака и ужина");
            assertThat(intent.locationFeatures()).contains(LocationFeature.FOOD_NEARBY);
            assertThat(intent.mealPlan()).isNull();
            assertThat(extract("Казань, где поужинать рядом").locationFeatures()).contains(LocationFeature.FOOD_NEARBY);
            assertThat(extract("Турция, завтрак включен").mealPlan()).isEqualTo(MealPlan.BREAKFAST);
        }

        @Test
        void facilitiesRentalsAndParking() {
            assertThat(extract("Пхукет, аренда байка, бассейн и бар").amenities())
                    .containsExactlyInAnyOrder(Amenity.MOTO_RENTAL, Amenity.POOL, Amenity.BAR);
            assertThat(extract("Крым, приедем на своем мотоцикле").amenities()).containsExactly(Amenity.MOTO_PARKING);
            assertThat(extract("Минск, едем на своей машине, нужна зарядка для электромобиля").amenities())
                    .containsExactlyInAnyOrder(Amenity.PARKING, Amenity.EV_CHARGING);
            assertThat(extract("Сочи, прокат авто и велосипедов").amenities())
                    .containsExactlyInAnyOrder(Amenity.CAR_RENTAL, Amenity.BIKE_RENTAL);
            assertThat(extract("Казань, отель с рестораном и спортзалом").amenities())
                    .containsExactlyInAnyOrder(Amenity.RESTAURANT, Amenity.GYM);
            assertThat(extract("Барселона").amenities()).isEmpty();
        }

        @Test
        void unknownPlaceAndLandmarkPhrases() {
            assertThat(extractor.extract("Хочу в Урюпинск").clarifications())
                    .anyMatch(c -> c.contains("«Урюпинск» пока нет в нашем каталоге"));
            assertThat(extract("Чэнду, хочу к пандам").proximity()).extracting(ProximityConstraint::landmark)
                    .containsExactly("пандам");
            assertThat(extract("Чэнду, рядом с базой панд").proximity()).extracting(ProximityConstraint::landmark)
                    .containsExactly("базой панд");
            assertThat(extract("Хочу к морю в Турцию").proximity()).isEmpty();
            assertThat(extract("Бангкок, подальше от шумной дороги").locationFeatures())
                    .containsExactly(LocationFeature.AWAY_FROM_TRAFFIC);
        }

        @Test
        void plainQueryHasNoSpecialNeeds() {
            SearchIntent intent = extract("Турция, всё включено, двое взрослых");
            assertThat(intent.beach()).isEmpty();
            assertThat(intent.activities()).isEmpty();
            assertThat(intent.access()).isEmpty();
            assertThat(intent.pets()).isEmpty();
            assertThat(intent.room()).isEmpty();
            assertThat(intent.visaFreeOnly()).isFalse();
            assertThat(intent.wildlife()).isEmpty();
            assertThat(intent.diet()).isEmpty();
            assertThat(intent.transit()).isEmpty();
        }
    }

    private SearchIntent extract(String query) {
        return extractor.extract(query).intent();
    }
}
