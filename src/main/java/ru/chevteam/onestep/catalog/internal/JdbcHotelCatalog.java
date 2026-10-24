package ru.chevteam.onestep.catalog.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.HotelDetails;
import ru.chevteam.onestep.catalog.Pricing;
import ru.chevteam.onestep.catalog.RateQuote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcHotelCatalog implements HotelCatalog {

    private final JdbcClient jdbc;

    JdbcHotelCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<HotelDetails> findById(UUID id) {
        List<HotelDetails.RoomRate> rates = jdbc.sql("""
                        SELECT id, room_name, meal_plan, max_adults, max_children, price_per_night, refundable
                          FROM room_rate WHERE hotel_id = :id ORDER BY price_per_night
                        """)
                .param("id", id)
                .query((rs, n) -> new HotelDetails.RoomRate(
                        rs.getObject("id", UUID.class),
                        rs.getString("room_name"),
                        rs.getString("meal_plan"),
                        rs.getInt("max_adults"),
                        rs.getInt("max_children"),
                        rs.getBigDecimal("price_per_night"),
                        rs.getBoolean("refundable")))
                .list();

        return jdbc.sql("""
                        SELECT h.*, ST_Y(h.location::geometry) AS lat, ST_X(h.location::geometry) AS lon,
                               ARRAY(SELECT a.amenity FROM hotel_amenity a WHERE a.hotel_id = h.id ORDER BY a.amenity) AS amenity_list
                          FROM hotel h WHERE h.id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new HotelDetails(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("country"),
                        rs.getString("city"),
                        rs.getString("district"),
                        rs.getString("address"),
                        rs.getInt("stars"),
                        rs.getBigDecimal("rating"),
                        rs.getInt("reviews_count"),
                        rs.getString("short_description"),
                        rs.getDouble("lat"),
                        rs.getDouble("lon"),
                        Arrays.asList((String[]) rs.getArray("amenity_list").getArray()),
                        new HotelDetails.GuestScores(
                                rs.getBigDecimal("quiet_score"),
                                rs.getBigDecimal("clean_score"),
                                rs.getBigDecimal("wifi_score"),
                                rs.getBigDecimal("family_score")),
                        rates))
                .optional();
    }

    @Override
    public Optional<RateQuote> quote(UUID rateId, LocalDate checkIn, int nights) {
        if (nights < 1) {
            throw new IllegalArgumentException("nights must be positive: " + nights);
        }
        return jdbc.sql("""
                        SELECT rr.id, rr.room_name, rr.meal_plan, rr.max_adults, rr.max_children,
                               rr.price_per_night, rr.refundable, h.id AS hotel_id, h.name AS hotel_name, h.city
                          FROM room_rate rr JOIN hotel h ON h.id = rr.hotel_id
                         WHERE rr.id = :rateId
                        """)
                .param("rateId", rateId)
                .query((rs, n) -> {
                    String city = rs.getString("city");
                    BigDecimal base = rs.getBigDecimal("price_per_night");
                    Map<Integer, BigDecimal> season = seasonCoefficients(city);
                    BigDecimal total = BigDecimal.ZERO;
                    for (int i = 0; i < nights; i++) {
                        int month = checkIn.plusDays(i).getMonthValue();
                        total = total.add(Pricing.nightPrice(base, season.get(month)));
                    }
                    return new RateQuote(
                            rs.getObject("id", UUID.class),
                            rs.getObject("hotel_id", UUID.class),
                            rs.getString("hotel_name"),
                            city,
                            rs.getString("room_name"),
                            rs.getString("meal_plan"),
                            rs.getInt("max_adults"),
                            rs.getInt("max_children"),
                            rs.getBoolean("refundable"),
                            nights,
                            total,
                            "RUB");
                })
                .optional();
    }

    private Map<Integer, BigDecimal> seasonCoefficients(String city) {
        Map<Integer, BigDecimal> result = new HashMap<>();
        jdbc.sql("SELECT month, coefficient FROM season_coefficient WHERE city = :city")
                .param("city", city)
                .query(rs -> {
                    result.put(rs.getInt("month"), rs.getBigDecimal("coefficient"));
                });
        return result;
    }
}
