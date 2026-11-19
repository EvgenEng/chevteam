package ru.chevteam.onestep.trip;

import java.util.List;

/**
 * Состояние разговора, которое присылает клиент.
 *
 * @param text       описание поездки своими словами; уточнения гостя дописываются через перевод строки
 * @param origin     ответ на вопрос «откуда едем» или {@code null}
 * @param reserve    ответ на вопрос о резерве на личные расходы («без резерва», «20 тысяч») или {@code null}
 * @param transport  вид транспорта, который гость выбрал сам, или {@code null}
 * @param excludeHotels отели, которые гость уже отклонил («другой отель»)
 */
public record TripRequest(String text, String origin, String reserve, TransportMode transport,
                          List<String> excludeHotels) {

    public TripRequest {
        excludeHotels = excludeHotels == null ? List.of() : List.copyOf(excludeHotels);
    }
}
