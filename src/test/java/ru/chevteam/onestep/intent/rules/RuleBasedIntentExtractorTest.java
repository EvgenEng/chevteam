package ru.chevteam.onestep.intent.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.Destination;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.TravelStyle;

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

            assertThat(intent.destination()).isEqualTo(Destination.city("Россия", "Сочи"));
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

            assertThat(intent.destination()).isEqualTo(Destination.city("Россия", "Санкт-Петербург"));
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
            "Камбоджа, Ангкор               | Камбоджа | -",
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
        assertThat(extract(query).destination()).isEqualTo(new Destination(country, city));
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
        assertThat(extract("Сочи, рядом китайский ресторан").destination()).isEqualTo(Destination.city("Россия", "Сочи"));
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

    private SearchIntent extract(String query) {
        return extractor.extract(query).intent();
    }
}
