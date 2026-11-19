package ru.chevteam.onestep.trip.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.trip.TransportMode;
import ru.chevteam.onestep.trip.TripOption.Leg;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TravelModelTest {

    private static final Places.Point YALTA_HOTEL = new Places.Point(44.4893, 34.1616);
    private static final Places.City MOSCOW = Places.origin("Москва").orElseThrow();
    private static final LocalDate IN = LocalDate.of(2027, 6, 12);
    private static final LocalDate OUT = LocalDate.of(2027, 6, 17);

    private static TravelModel.Computed toYalta(TransportMode mode, int adults, List<Integer> kids) {
        Places.Arrival arrival = Places.arrival("Россия", "Крым", "Ялта", YALTA_HOTEL);
        return TravelModel.compute(mode, MOSCOW, arrival, YALTA_HOTEL, "Ореанда", IN, OUT, adults, kids);
    }

    @Test
    void noPlanesToCrimea() {
        TravelModel.Computed plane = toYalta(TransportMode.PLANE, 2, List.of());

        assertThat(plane.available()).isFalse();
        assertThat(plane.reason()).contains("Симферопол");
    }

    @Test
    void trainArrivesAtSimferopolAndTransferReachesHotelBeforeEvening() {
        TravelModel.Computed train = toYalta(TransportMode.TRAIN, 2, List.of(6)); // кресло — детям до 7 лет

        assertThat(train.available()).isTrue();
        assertThat(train.hub()).isEqualTo("Симферополь · вокзал");
        assertThat(train.outMinutes()).isBetween(26 * 60, 36 * 60); // «Таврия» идёт около суток с лишним
        assertThat(train.hotelArrival().toLocalDate()).isEqualTo(IN);
        assertThat(train.hotelArrival().toLocalTime()).isBetween(LocalTime.of(11, 0), LocalTime.of(16, 0));
        assertThat(train.childSeats()).isEqualTo(1);
        assertThat(train.out()).extracting(Leg::kind).containsExactly("TRAVEL_OUT", "TRANSFER_IN");
        assertThat(train.back()).extracting(Leg::kind).containsExactly("TRANSFER_OUT", "TRAVEL_BACK");
        // обратно: выезд в 12:00, трансфер, 45 минут запаса — поезд не раньше
        Leg back = train.back().get(1);
        assertThat(back.departure()).isAfterOrEqualTo(train.back().get(0).arrival().plusMinutes(45));
    }

    @Test
    void bigGroupNeedsTwoTransferCars() {
        TravelModel.Computed train = toYalta(TransportMode.TRAIN, 3, List.of(7, 10));

        assertThat(train.cars()).isEqualTo(2);
        assertThat(train.transfers()).isPositive();
    }

    @Test
    void longDriveIncludesOvernightStop() {
        TravelModel.Computed car = toYalta(TransportMode.CAR, 2, List.of());

        assertThat(car.available()).isTrue();
        assertThat(car.overnight()).isPositive();
        assertThat(car.transfers()).isZero();
        assertThat(car.caveat()).contains("ночёвка");
    }

    @Test
    void sochiCanBeReachedByPlaneAndItIsFaster() {
        Places.Point hotel = new Places.Point(43.58, 39.72);
        Places.Arrival arrival = Places.arrival("Россия", "Краснодарский край", "Сочи", hotel);
        TravelModel.Computed plane = TravelModel.compute(TransportMode.PLANE, MOSCOW, arrival, hotel, "Отель", IN, OUT, 2, List.of());
        TravelModel.Computed train = TravelModel.compute(TransportMode.TRAIN, MOSCOW, arrival, hotel, "Отель", IN, OUT, 2, List.of());

        assertThat(plane.available()).isTrue();
        assertThat(plane.outMinutes()).isLessThan(train.outMinutes());
        assertThat(plane.departure().getHour()).isGreaterThanOrEqualTo(6);
    }
}
