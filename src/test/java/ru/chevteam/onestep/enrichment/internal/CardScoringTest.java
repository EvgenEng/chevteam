package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.CardScore;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.FactSource;
import ru.chevteam.onestep.enrichment.HotelFact;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Уровни карточки и задачи отельеру: что именно поднимет карточку. */
class CardScoringTest {

    static final UUID HOTEL = UUID.randomUUID();

    static ClaimCheck check(String code, ClaimStatus status) {
        return new ClaimCheck(code, code, "заявлено", "по карте", status);
    }

    static HotelFact fact(String code, String level, String value) {
        return new HotelFact(HOTEL, code, code, value, level, null, 0.8, FactSource.OSM, "метод", "доказательство", Instant.now());
    }

    static List<ClaimCheck> confirmed(int n) {
        List<ClaimCheck> list = new ArrayList<>();
        for (String code : CardScoring.ORDER.subList(0, n)) {
            list.add(check(code, ClaimStatus.CONFIRMED));
        }
        return list;
    }

    @Test
    void emptyCardIsNew() {
        CardScore s = CardScoring.score(List.of());
        assertThat(s.level()).isEqualTo(CardScore.Level.NEW);
        assertThat(s.total()).isEqualTo(9);
        assertThat(s.trust()).isNull();
        assertThat(CardScoring.next(s)).contains("Заполните ещё 6 из 9");
    }

    @Test
    void twoThirdsConfirmedMakesItVerified() {
        assertThat(CardScoring.score(confirmed(5)).level()).isEqualTo(CardScore.Level.NEW);
        CardScore s = CardScoring.score(confirmed(6));
        assertThat(s.level()).isEqualTo(CardScore.Level.VERIFIED);
        assertThat(CardScoring.next(s)).contains("Проверенная карточка");
    }

    @Test
    void oneDisputeHoldsTheCardAtFilled() {
        List<ClaimCheck> checks = new ArrayList<>(confirmed(6));
        checks.set(0, check(checks.get(0).code(), ClaimStatus.DISPUTED));
        CardScore s = CardScoring.score(checks);
        assertThat(s.level()).isEqualTo(CardScore.Level.FILLED);
        assertThat(s.disputes()).isEqualTo(1);
        assertThat(CardScoring.next(s)).contains("Исправьте расхождения");
    }

    @Test
    void unverifiableClaimsFillButDoNotProve() {
        List<ClaimCheck> checks = new ArrayList<>();
        for (String code : CardScoring.ORDER.subList(0, 6)) {
            checks.add(check(code, ClaimStatus.UNVERIFIED));
        }
        CardScore s = CardScoring.score(checks);
        assertThat(s.level()).isEqualTo(CardScore.Level.FILLED);
        assertThat(s.trust()).isNull();
    }

    @Test
    void tasksGoFixFirstThenConfirmThenTell() {
        List<HotelFact> facts = List.of(fact("ROAD_NOISE", "HIGH", "у оживлённой улицы"), fact("SEA_DISTANCE", "NEAR", "300 м"),
                fact("BEACH_SURFACE", "UNKNOWN", "нет данных"));
        List<ClaimCheck> checks = List.of(check("ROAD_NOISE", ClaimStatus.DISPUTED));
        List<HotelierTask> tasks = CardScoring.tasks(facts, checks);

        assertThat(tasks).hasSize(9);
        assertThat(tasks.get(0).kind()).isEqualTo(HotelierTask.Kind.FIX);
        assertThat(tasks.get(0).code()).isEqualTo("ROAD_NOISE");
        assertThat(tasks.get(0).suggestedLevel()).isEqualTo("HIGH");
        assertThat(tasks.get(1).kind()).isEqualTo(HotelierTask.Kind.CONFIRM);
        assertThat(tasks.get(1).suggestedText()).isEqualTo("до моря до 10 минут пешком");
        // «нет данных» на карте — не повод что-то подтверждать: просим рассказать
        assertThat(tasks).filteredOn(t -> t.code().equals("BEACH_SURFACE"))
                .singleElement().satisfies(t -> assertThat(t.kind()).isEqualTo(HotelierTask.Kind.TELL));
    }

    @Test
    void confirmedClaimsLeaveNoTask() {
        List<HotelierTask> tasks = CardScoring.tasks(List.of(fact("SEA_DISTANCE", "NEAR", "300 м")),
                List.of(check("SEA_DISTANCE", ClaimStatus.CONFIRMED)));
        assertThat(tasks).noneMatch(t -> t.code().equals("SEA_DISTANCE"));
    }
}
