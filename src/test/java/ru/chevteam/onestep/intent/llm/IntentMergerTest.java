package ru.chevteam.onestep.intent.llm;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.Budget;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TransitKind;
import ru.chevteam.onestep.intent.Wildlife;
import ru.chevteam.onestep.intent.rules.RuleAnalysis;
import ru.chevteam.onestep.intent.rules.RuleBasedIntentExtractor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentMergerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private final RuleBasedIntentExtractor rules = new RuleBasedIntentExtractor(
            Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneId.of("Europe/Moscow")));

    @Test
    void explicitRuleValuesBeatLlmHallucinations() {
        RuleAnalysis analysis = rules.analyze("Сочи, бюджет 120 тысяч, 5 ночей");
        LlmIntentPayload llm = payload("Россия", "Сочи", null, 14, 999_999L, null);

        ExtractionResult result = IntentMerger.merge(llm, analysis, TODAY);

        assertThat(result.source()).isEqualTo(IntentSource.AI);
        assertThat(result.intent().budget()).isEqualTo(Budget.total(120_000));
        assertThat(result.intent().stay().nights()).isEqualTo(5);
    }

    @Test
    void llmFillsWhatRulesOnlyGuessed() {
        RuleAnalysis analysis = rules.analyze("Хочу в Мармарис летом, всё по высшему разряду");
        LlmIntentPayload llm = payload("Турция", "Мармарис", 7, 10, null, 5);

        ExtractionResult result = IntentMerger.merge(llm, analysis, TODAY);

        assertThat(result.intent().destination().city()).isEqualTo("Мармарис");
        assertThat(result.intent().stay().month()).isEqualTo(YearMonth.of(2027, 7));
        assertThat(result.intent().stay().nights()).isEqualTo(10);
        assertThat(result.intent().minStars()).isEqualTo(5);
        // допущения по ночам и датам сняты, вопрос «куда» — тоже
        assertThat(result.assumptions()).noneMatch(a -> a.contains("ночей") || a.contains("Даты"));
        assertThat(result.clarifications()).noneMatch(c -> c.startsWith("Куда"));
    }

    @Test
    void invalidLlmValuesAreDroppedNotFatal() {
        RuleAnalysis analysis = rules.analyze("Сочи");
        LlmIntentPayload llm = new LlmIntentPayload("Россия", null, "Сочи", 13, "not-a-date", 0, 99,
                List.of(4, 40), -5L, "TOTAL", 9, "SPACE_FOOD", List.of("POOL", "TELEPORT"),
                null, null, null, null, null, List.of("SANDY", "LAVA"), null, null, null, null, null, null, null, null);

        ExtractionResult result = IntentMerger.merge(llm, analysis, TODAY);

        assertThat(result.intent().stay().month()).isNull();
        assertThat(result.intent().stay().nights()).isEqualTo(7);      // дефолт правил
        assertThat(result.intent().guests().adults()).isEqualTo(2);    // дефолт правил
        assertThat(result.intent().guests().childrenAges()).isEmpty(); // 40 лет — не ребёнок
        assertThat(result.intent().budget()).isNull();
        assertThat(result.intent().minStars()).isNull();
        assertThat(result.intent().mealPlan()).isNull();
        assertThat(result.intent().amenities()).containsExactly(Amenity.POOL);
        assertThat(result.intent().beach()).containsExactly(BeachPreference.SANDY);
    }

    @Test
    void setsAreUnited() {
        RuleAnalysis analysis = rules.analyze("Турция, всё включено, аквапарк");
        LlmIntentPayload llm = new LlmIntentPayload("Турция", null, null, null, null, null, null, null, null, null,
                null, "HALF_BOARD", List.of("KIDS_CLUB"), null, null, null, null, null,
                null, List.of("DIVING"), List.of("SENIORS"), List.of("BABY_COT"), List.of("DOG", "DRAGON"), true, List.of("DOLPHINS"),
                List.of("HALAL", "PORK"), List.of(new LlmIntentPayload.Transit("AIRPORT", 40), new LlmIntentPayload.Transit("SPACEPORT", 5)));

        ExtractionResult result = IntentMerger.merge(llm, analysis, TODAY);

        assertThat(result.intent().mealPlan()).isEqualTo(MealPlan.ALL_INCLUSIVE);
        assertThat(result.intent().amenities()).containsExactlyInAnyOrder(Amenity.AQUAPARK, Amenity.KIDS_CLUB);
        assertThat(result.intent().activities()).containsExactly(BeachActivity.DIVING);
        assertThat(result.intent().access()).containsExactly(AccessNeed.SENIORS);
        assertThat(result.intent().room()).containsExactly(RoomFeature.BABY_COT);
        assertThat(result.intent().pets()).containsExactly(PetKind.DOG);
        assertThat(result.intent().visaFreeOnly()).isTrue();
        assertThat(result.intent().wildlife()).containsExactly(Wildlife.DOLPHINS);
        assertThat(result.intent().diet()).containsExactly(DietNeed.HALAL);
        assertThat(result.intent().transit()).containsExactly(new TransitConstraint(TransitKind.AIRPORT, 40));
    }

    private static LlmIntentPayload payload(String country, String city, Integer month, Integer nights,
                                            Long budget, Integer stars) {
        return new LlmIntentPayload(country, null, city, month, null, nights, null, null, budget, "TOTAL", stars,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
