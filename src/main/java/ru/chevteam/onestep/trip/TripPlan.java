package ru.chevteam.onestep.trip;

import ru.chevteam.onestep.search.HotelMatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ответ О!Плана на очередное сообщение гостя.
 *
 * @param stage    ASK — нужен ответ на вопрос; OPTIONS — готовы варианты; EMPTY — в бюджет ничего не вошло
 * @param message  что ответить в чате
 * @param question единственный уточняющий вопрос или {@code null}
 * @param chips    как система поняла поездку: «Ялта», «12–17 нояб.», «3 взрослых», «Весь бюджет: 150 000 ₽»
 * @param context  параметры поездки, по которым клиент потом просит пересчёт
 * @param options  до трёх вариантов: поездка целиком и карточка отеля из поиска
 * @param compared сколько отелей сравнили
 */
public record TripPlan(String stage, String message, Question question, List<String> chips, Context context,
                       List<Option> options, int compared, List<String> assumptions, String notice) {

    public TripPlan {
        chips = chips == null ? List.of() : List.copyOf(chips);
        options = options == null ? List.of() : List.copyOf(options);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }

    /**
     * @param field ORIGIN, RESERVE, DESTINATION
     */
    public record Question(String field, String title, String text, List<String> chips) {
    }

    /**
     * @param origin    город отправления; {@code null} — только отель
     * @param transport вид транспорта, который гость назвал сам
     */
    public record Context(String destination, String origin, TransportMode transport, LocalDate checkIn, int nights,
                          int adults, List<Integer> childrenAges, BigDecimal budget, BigDecimal reserve,
                          List<String> wishes) {
    }

    public record Option(TripOption trip, HotelMatch hotel) {
    }
}
