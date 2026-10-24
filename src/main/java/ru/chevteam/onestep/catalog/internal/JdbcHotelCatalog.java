package ru.chevteam.onestep.catalog.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.HotelDetails;

import java.util.Arrays;
import java.util.List;
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
}
