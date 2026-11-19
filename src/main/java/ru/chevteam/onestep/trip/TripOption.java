package ru.chevteam.onestep.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Поездка целиком: проживание, дорога, трансферы, расписание, состав цены и что проверить.
 *
 * @param route       выбранная дорога или {@code null}, если гость едет «только отель»
 * @param routes      все виды транспорта с ценой всей поездки — чтобы сравнить и переключиться
 * @param timeline    по шагам: дорога туда → трансфер → проживание → трансфер → дорога обратно
 * @param spendable   бюджет за вычетом резерва — столько можно потратить на поездку
 * @param fits        укладывается ли в бюджет; {@code null}, если бюджет не задан
 * @param budgetLeft  сколько остаётся (может быть отрицательным)
 * @param summary     «Поезд → трансфер → отель → трансфер → обратно»
 * @param travelNote  «26 ч 23 мин до отеля · есть ночной участок»
 */
public record TripOption(
        UUID hotelId,
        String hotelName,
        String hotelCity,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int adults,
        List<Integer> childrenAges,
        String origin,
        Stay stay,
        Route route,
        List<Route> routes,
        List<Leg> timeline,
        List<CostLine> costs,
        BigDecimal total,
        BigDecimal budget,
        BigDecimal reserve,
        BigDecimal spendable,
        Boolean fits,
        BigDecimal budgetLeft,
        List<Check> checks,
        String summary,
        String travelNote,
        List<String> notes) {

    /**
     * @param rooms сколько номеров этого тарифа нужно, чтобы разместить всех
     */
    public record Stay(UUID rateId, String roomName, String mealPlan, int rooms, boolean refundable,
                       BigDecimal total, BigDecimal perNight) {
    }

    /**
     * Вид транспорта целиком: туда, обратно и трансферы.
     *
     * @param available   можно ли так доехать (в Крым самолётом — нельзя)
     * @param reason      почему недоступно или {@code null}
     * @param roundTrip   дорога туда и обратно на всех
     * @param transfers   оба трансфера (у машины — 0)
     * @param outMinutes  от двери до отеля
     * @param backMinutes от отеля до двери
     * @param tripTotal   вся поездка с этим транспортом
     * @param delta       разница с выбранным вариантом
     * @param nightLeg    есть ли в дороге ночной участок
     */
    public record Route(TransportMode mode, String title, boolean available, String reason, String hub,
                        BigDecimal roundTrip, BigDecimal transfers, int outMinutes, int backMinutes,
                        String pros, String caveat, BigDecimal tripTotal, BigDecimal delta,
                        boolean selected, boolean nightLeg, int cars, int childSeats) {
    }

    /**
     * @param kind TRAVEL_OUT, TRANSFER_IN, STAY, TRANSFER_OUT, TRAVEL_BACK
     */
    public record Leg(String kind, String title, String from, String to,
                      LocalDateTime departure, LocalDateTime arrival, String note) {
    }

    public record CostLine(String code, String title, BigDecimal amount) {
    }

    /**
     * @param status OK — проверено; ASK — уточнить перед поездкой; FAIL — не сходится
     */
    public record Check(String status, String text) {
    }
}
