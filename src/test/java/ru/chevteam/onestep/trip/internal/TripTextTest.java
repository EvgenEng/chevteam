package ru.chevteam.onestep.trip.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.trip.TransportMode;

import static org.assertj.core.api.Assertions.assertThat;

class TripTextTest {

    @Test
    void travelWordsDoNotReachHotelSearch() {
        TripText.Parsed p = TripText.read(
                "Ялта из Москвы, 10–17 января, двое взрослых и ребёнок 7 лет, до 120 тысяч на дорогу, отель и трансферы, поездом");

        assertThat(p.origin().name()).isEqualTo("Москва");
        assertThat(p.transport()).isEqualTo(TransportMode.TRAIN);
        // «трансфер» стал бы требованием к отелю, «Москва» — направлением
        assertThat(p.searchText()).doesNotContainIgnoringCase("трансфер").doesNotContain("Москв").doesNotContain("поезд")
                .contains("Ялта").contains("до 120 тысяч");
    }

    @Test
    void reserveIsReadFromTextAndRemovedFromBudget() {
        TripText.Parsed p = TripText.read("Алушта, 5 ночей в июне, до 100 тысяч, резерв на личные расходы 20 тысяч");

        assertThat(p.reserve()).isEqualByComparingTo("20000");
        assertThat(p.searchText()).doesNotContain("20").contains("до 100 тысяч");
    }

    @Test
    void reserveAnswers() {
        assertThat(TripText.reserveAnswer("Без отдельного резерва")).contains(java.math.BigDecimal.ZERO);
        assertThat(TripText.reserveAnswer("Оставить 10 000 ₽").orElseThrow()).isEqualByComparingTo("10000");
        assertThat(TripText.reserveAnswer("резерв 15").orElseThrow()).isEqualByComparingTo("15000");
        assertThat(TripText.reserveAnswer("что-то непонятное")).isEmpty();
    }

    @Test
    void hotelOnlyAndUnknownOrigin() {
        assertThat(TripText.read("Алушта на неделю, только отель").hotelOnly()).isTrue();
        TripText.Parsed p = TripText.read("из Урюпинска в Ялту на неделю");
        assertThat(p.origin()).isNull();
        assertThat(p.unknownOrigin()).isEqualTo("Урюпинска");
    }

    @Test
    void originAnswersInDifferentForms() {
        assertThat(Places.origin("Отправляемся из Москвы")).map(Places.City::name).contains("Москва");
        assertThat(Places.origin("Питер")).map(Places.City::name).contains("Санкт-Петербург");
        assertThat(Places.origin("из Нижнего Новгорода")).map(Places.City::name).contains("Нижний Новгород");
        assertThat(Places.origin("Другой город")).isEmpty();
    }
}
