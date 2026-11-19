package ru.chevteam.onestep.trip.internal;

import ru.chevteam.onestep.trip.TransportMode;
import ru.chevteam.onestep.trip.TripOption.Leg;
import ru.chevteam.onestep.trip.internal.Places.Arrival;
import ru.chevteam.onestep.trip.internal.Places.City;
import ru.chevteam.onestep.trip.internal.Places.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Демо-модель дороги: время и цена по расстоянию. Без внешних API, детерминированно — одинаковый запрос
 * даёт одинаковый план. Все коэффициенты собраны здесь, чтобы их можно было заменить реальными тарифами.
 */
final class TravelModel {

    static final LocalTime CHECK_IN = LocalTime.of(14, 0);
    static final LocalTime CHECK_OUT = LocalTime.of(12, 0);

    /** Сколько человек в одной машине трансфера и в своей машине. */
    static final int TRANSFER_SEATS = 4;
    static final int CAR_SEATS = 5;
    /** Дети до этого возраста едут в детском кресле. */
    static final int CHILD_SEAT_UNDER = 7;

    private TravelModel() {
    }

    /**
     * Посчитанный вид транспорта: деньги на всех, время от двери до двери и расписание.
     *
     * @param roundTrip  дорога туда и обратно на всех
     * @param transfers  оба трансфера
     * @param overnight  ночёвка в пути на машине (туда и обратно), уже входит в {@code roundTrip}
     * @param waitMinutes сколько ждать заселения, если приехали раньше 14:00
     */
    record Computed(TransportMode mode, boolean available, String reason, String hub, BigDecimal roundTrip,
                    BigDecimal transfers, BigDecimal overnight, int outMinutes, int backMinutes, String pros,
                    String caveat, boolean nightLeg, int cars, int childSeats, List<Leg> out, List<Leg> back,
                    LocalDateTime departure, LocalDateTime hotelArrival, LocalDateTime hotelLeave,
                    LocalDateTime homeArrival, int bufferMinutes, double distanceKm) {

        static Computed unavailable(TransportMode mode, String reason) {
            return new Computed(mode, false, reason, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                    null, null, false, 0, 0, List.of(), List.of(), null, null, null, null, 0, 0);
        }

        BigDecimal travel() {
            return roundTrip.add(transfers);
        }
    }

    static Computed compute(TransportMode mode, City from, Arrival arrival, Point hotel, String hotelName,
                            LocalDate checkIn, LocalDate checkOut, int adults, List<Integer> kids) {
        return switch (mode) {
            case PLANE -> plane(from, arrival, hotel, hotelName, checkIn, checkOut, adults, kids);
            case TRAIN -> train(from, arrival, hotel, hotelName, checkIn, checkOut, adults, kids);
            case CAR -> car(from, arrival, hotel, hotelName, checkIn, checkOut, adults, kids);
        };
    }

    // ------------------------------------------------------------------ самолёт

    private static Computed plane(City from, Arrival arrival, Point hotel, String hotelName,
                                  LocalDate checkIn, LocalDate checkOut, int adults, List<Integer> kids) {
        if (arrival.planeBlocked() != null) {
            return Computed.unavailable(TransportMode.PLANE, arrival.planeBlocked());
        }
        if (arrival.airport() == null) {
            return Computed.unavailable(TransportMode.PLANE, "Рядом с местом нет аэропорта");
        }
        if (from.airport() == null) {
            return Computed.unavailable(TransportMode.PLANE, "В демо-модели нет рейсов из " + from.genitive());
        }
        double km = Places.km(from.point(), arrival.airportPoint());
        if (km < 400) {
            return Computed.unavailable(TransportMode.PLANE, "Близко — самолёт не нужен");
        }
        int flight = (int) Math.round(40 + km / 800.0 * 60);
        int baggage = 30;
        int buffer = 120;
        // взрослые и дети от 2 лет — с местом; дети 2–11 — 75 % тарифа; малыши до 2 лет — без места, бесплатно
        BigDecimal adultFare = money(3500 + 5.0 * km);
        BigDecimal oneWay = adultFare.multiply(BigDecimal.valueOf(adults));
        for (int age : kids) {
            if (age >= 12) {
                oneWay = oneWay.add(adultFare);
            } else if (age >= 2) {
                oneWay = oneWay.add(money(adultFare.doubleValue() * 0.75));
            }
        }
        return withTransfers(TransportMode.PLANE, from.airport(), arrival.airport(), arrival.airportPoint(), hotel,
                hotelName, checkIn, checkOut, adults, kids, flight, baggage, buffer, oneWay.multiply(BigDecimal.TWO),
                "Быстрее на дальнем направлении", "Нужен запас в аэропорту; багаж оплачивается по тарифу",
                "Самолёт", km);
    }

    // ------------------------------------------------------------------ поезд

    private static Computed train(City from, Arrival arrival, Point hotel, String hotelName,
                                  LocalDate checkIn, LocalDate checkOut, int adults, List<Integer> kids) {
        if (!arrival.trainOk() || arrival.station() == null) {
            return Computed.unavailable(TransportMode.TRAIN, "Поездом сюда не доехать");
        }
        if (from.station() == null) {
            return Computed.unavailable(TransportMode.TRAIN, "В демо-модели нет поездов из " + from.genitive());
        }
        double km = Places.km(from.point(), arrival.stationPoint());
        if (km < 100) {
            return Computed.unavailable(TransportMode.TRAIN, "Близко — проще на машине или автобусе");
        }
        double railKm = km * 1.3;
        int ride = (int) Math.round(railKm / 52.0 * 60);
        // купе: взрослые и дети от 10 лет — полный тариф, 5–9 лет — половина, до 5 лет — без места
        BigDecimal adultFare = money(1200 + 3.6 * railKm);
        BigDecimal oneWay = adultFare.multiply(BigDecimal.valueOf(adults));
        for (int age : kids) {
            if (age >= 10) {
                oneWay = oneWay.add(adultFare);
            } else if (age >= 5) {
                oneWay = oneWay.add(money(adultFare.doubleValue() * 0.5));
            }
        }
        return withTransfers(TransportMode.TRAIN, from.station(), arrival.station(), arrival.stationPoint(), hotel,
                hotelName, checkIn, checkOut, adults, kids, ride, 10, 45, oneWay.multiply(BigDecimal.TWO),
                "Прибытие в город, меньше времени на формальности",
                "Дальняя дорога может включать ночь; места и багаж зависят от тарифа", "Поезд", km);
    }

    // ------------------------------------------------------------------ самолёт или поезд + трансфер

    private static Computed withTransfers(TransportMode mode, String fromHub, String toHub, Point hubPoint, Point hotel,
                                          String hotelName, LocalDate checkIn, LocalDate checkOut, int adults,
                                          List<Integer> kids, int ride, int arrivalWait, int buffer,
                                          BigDecimal roundTrip, String pros, String caveat, String noun, double km) {
        int people = adults + kids.size();
        int cars = ceilDiv(people, TRANSFER_SEATS);
        int seats = (int) kids.stream().filter(a -> a < CHILD_SEAT_UNDER).count();
        double roadKm = Places.km(hubPoint, hotel) * 1.35;
        int transferMin = (int) Math.round(roadKm / 45.0 * 60) + 10;
        BigDecimal perCar = money(Math.max(1500, Math.round(32 * roadKm / 100.0) * 100.0));
        BigDecimal transfers = perCar.multiply(BigDecimal.valueOf((long) cars * 2));

        // туда: успеть к заселению в 14:00 — прибыть к вокзалу или в аэропорт около 13:00 минус трансфер
        LocalDateTime hubTarget = checkIn.atTime(13, 0).minusMinutes(transferMin + arrivalWait);
        LocalDateTime departure = roundDown(hubTarget.minusMinutes(ride), 5);
        if (departure.getHour() < 6) {
            // ночью не отправляем: самолёт и поезд — с 6 утра, прибытие сдвигается ближе к заселению
            departure = departure.toLocalDate().atTime(6, 0);
        }
        LocalDateTime hubArrival = departure.plusMinutes(ride);
        LocalDateTime transferStart = hubArrival.plusMinutes(arrivalWait);
        LocalDateTime hotelArrival = transferStart.plusMinutes(transferMin);

        // обратно: выезд в 12:00, трансфер, запас перед отправлением
        LocalDateTime leave = checkOut.atTime(CHECK_OUT);
        LocalDateTime hubBack = leave.plusMinutes(transferMin);
        LocalDateTime backDeparture = roundUp(hubBack.plusMinutes(buffer), 5);
        LocalDateTime home = backDeparture.plusMinutes(ride);

        String seatNote = cars + " авто" + (seats > 0 ? " · детских кресел: " + seats : "");
        String luggage = mode == TransportMode.PLANE ? "Нормы багажа по тарифу авиакомпании" : "Нормы багажа по билету нужно уточнить";
        List<Leg> out = List.of(
                new Leg("TRAVEL_OUT", noun + " туда", fromHub, toHub, departure, hubArrival,
                        "В пути " + duration(ride) + (mode == TransportMode.PLANE ? " · приехать в аэропорт за 2 ч" : "")),
                new Leg("TRANSFER_IN", "Трансфер в отель", toHub, hotelName, transferStart, hotelArrival,
                        seatNote + " · " + luggage));
        List<Leg> back = List.of(
                new Leg("TRANSFER_OUT", "Обратный трансфер", hotelName, toHub, leave, hubBack, seatNote),
                new Leg("TRAVEL_BACK", noun + " обратно", toHub, fromHub, backDeparture, home,
                        "В пути " + duration(ride)));
        int outMinutes = (int) ChronoUnit.MINUTES.between(departure.minusMinutes(buffer), hotelArrival);
        int backMinutes = (int) ChronoUnit.MINUTES.between(leave, home);
        boolean night = crossesNight(departure, hubArrival) || crossesNight(backDeparture, home);
        return new Computed(mode, true, null, toHub, roundTrip, transfers, BigDecimal.ZERO, outMinutes, backMinutes,
                pros, caveat, night, cars, seats, out, back, departure, hotelArrival, leave, home, buffer, km);
    }

    // ------------------------------------------------------------------ машина

    private static Computed car(City from, Arrival arrival, Point hotel, String hotelName,
                                LocalDate checkIn, LocalDate checkOut, int adults, List<Integer> kids) {
        if (!arrival.carOk()) {
            return Computed.unavailable(TransportMode.CAR, "На машине сюда не доехать");
        }
        double km = Places.km(from.point(), hotel);
        double roadKm = km * 1.3;
        if (roadKm > 4000) {
            return Computed.unavailable(TransportMode.CAR, "Слишком далеко для поездки на машине");
        }
        int people = adults + kids.size();
        int cars = ceilDiv(people, CAR_SEATS);
        int seats = (int) kids.stream().filter(a -> a < CHILD_SEAT_UNDER).count();
        int drive = (int) Math.round(roadKm / 70.0 * 60);
        drive += drive / 180 * 20; // остановка 20 минут каждые 3 часа
        boolean overnightStop = drive > 12 * 60;
        int total = drive + (overnightStop ? 9 * 60 : 0);
        // бензин ≈ 6 ₽/км, платные трассы ≈ 1 ₽/км на дальних маршрутах, ночёвка в пути ≈ 4 500 ₽ на машину
        double perKm = 6.0 + (roadKm > 400 ? 1.0 : 0);
        BigDecimal fuel = money(perKm * roadKm * 2 * cars);
        BigDecimal overnight = overnightStop ? money(4500.0 * 2 * cars) : BigDecimal.ZERO;

        LocalDateTime arrive = checkIn.atTime(CHECK_IN);
        LocalDateTime departure = roundDown(arrive.minusMinutes(total), 10);
        if (departure.getHour() < 6) {
            departure = departure.toLocalDate().atTime(6, 0);
        }
        LocalDateTime hotelArrival = departure.plusMinutes(total);
        LocalDateTime leave = checkOut.atTime(CHECK_OUT);
        LocalDateTime home = leave.plusMinutes(total);
        String road = "≈" + String.format("%,d", Math.round(roadKm)).replace(',', ' ') + " км"
                + (overnightStop ? " · с ночёвкой в пути" : "")
                + (seats > 0 ? " · детских кресел: " + seats : "");
        List<Leg> out = List.of(new Leg("TRAVEL_OUT", "На машине туда", from.name(), hotelName, departure, hotelArrival,
                road + " · за рулём " + duration(drive)));
        List<Leg> back = List.of(new Leg("TRAVEL_BACK", "На машине обратно", hotelName, from.name(), leave, home, road));
        String caveat = overnightStop
                ? "Больше 12 часов за рулём — заложена ночёвка в пути; платные дороги учтены приблизительно"
                : "Платные дороги и парковка учтены приблизительно";
        boolean night = !overnightStop && (crossesNight(departure, hotelArrival) || crossesNight(leave, home));
        return new Computed(TransportMode.CAR, true, null, null, fuel.add(overnight), BigDecimal.ZERO, overnight,
                total, total, "Своя машина на месте, багаж без ограничений", caveat, night, cars, seats, out, back,
                departure, hotelArrival, leave, home, 0, km);
    }

    // ------------------------------------------------------------------ helpers

    /** Какой транспорт предложить первым: деньги плюс цена времени в пути; долгие часы за рулём — дороже. */
    static double burden(Computed c, int people) {
        double hours = (c.outMinutes() + c.backMinutes()) / 60.0;
        double hourValue = c.mode() == TransportMode.CAR && c.outMinutes() > 8 * 60 ? 500 : 250;
        return c.travel().doubleValue() + hours * hourValue * people;
    }

    static boolean crossesNight(LocalDateTime from, LocalDateTime to) {
        LocalDateTime t = from;
        while (t.isBefore(to)) {
            if (t.getHour() < 6) {
                return true;
            }
            t = t.plusMinutes(30);
        }
        return to.getHour() < 6 && to.getHour() > 0;
    }

    static String duration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h == 0 ? m + " мин" : m == 0 ? h + " ч" : h + " ч " + m + " мин";
    }

    static BigDecimal money(double rubles) {
        return BigDecimal.valueOf(rubles).divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static LocalDateTime roundDown(LocalDateTime t, int step) {
        LocalDateTime m = t.truncatedTo(ChronoUnit.MINUTES);
        return m.minusMinutes(m.getMinute() % step);
    }

    private static LocalDateTime roundUp(LocalDateTime t, int step) {
        LocalDateTime m = t.truncatedTo(ChronoUnit.MINUTES);
        int rest = m.getMinute() % step;
        return rest == 0 ? m : m.plusMinutes(step - rest);
    }

    static List<Leg> concat(List<Leg> a, Leg stay, List<Leg> b) {
        List<Leg> all = new ArrayList<>(a);
        all.add(stay);
        all.addAll(b);
        return all;
    }
}
