package ru.chevteam.onestep.search.internal;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.NearbyPlace;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Выборка кандидатов одним запросом: отель + самый дешёвый подходящий тариф (LATERAL),
 * сезонный коэффициент, расстояние до достопримечательности (PostGIS) и что интересного рядом.
 * <p>
 * SQL собирается из заранее заданных фрагментов; пользовательские значения — только через параметры.
 */
@Repository
class CandidateRepository {

    static final int NEARBY_RADIUS_M = 1500;

    private static final String SELECT = """
            SELECT h.id, h.name, h.country, h.city, h.district, h.stars, h.rating, h.reviews_count,
                   ST_Y(h.location::geometry) AS lat, ST_X(h.location::geometry) AS lon,
                   h.beach_line, h.distance_to_beach_m, h.distance_to_center_m, h.airport_transfer_min,
                   h.quiet_score, h.clean_score, h.wifi_score, h.family_score, h.short_description,
                   r.room_name, r.meal_plan, r.price_per_night, r.refundable,
                   COALESCE(sc.coefficient, 1.0) AS season_coefficient,
                   ARRAY(SELECT a.amenity FROM hotel_amenity a WHERE a.hotel_id = h.id ORDER BY a.amenity) AS amenities,
                   ARRAY(SELECT l.name || '|' || ROUND(ST_Distance(h.location, l.location))::int
                           FROM landmark l
                          WHERE l.city = h.city AND ST_DWithin(h.location, l.location, %d)
                          ORDER BY ST_Distance(h.location, l.location)
                          LIMIT 3) AS nearby,
                   %s AS landmark_distance_m
              FROM hotel h
              JOIN LATERAL (
                    SELECT rr.room_name, rr.meal_plan, rr.price_per_night, rr.refundable
                      FROM room_rate rr
                     WHERE rr.hotel_id = h.id
                       AND rr.max_adults >= :adults
                       AND rr.max_children >= :children
                       %s
                     ORDER BY rr.price_per_night
                     LIMIT 1
                   ) r ON TRUE
              LEFT JOIN season_coefficient sc ON sc.city = h.city AND sc.month = :month
              %s
             WHERE TRUE
            """;

    private final JdbcClient jdbc;

    CandidateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ResolvedLandmark> resolveLandmark(String city, String word) {
        return jdbc.sql("""
                        SELECT l.id, l.name
                          FROM landmark l
                         WHERE l.city = :city
                           AND EXISTS (SELECT 1 FROM unnest(l.aliases) alias WHERE :word LIKE alias || '%')
                         ORDER BY l.name
                         LIMIT 1
                        """)
                .param("city", city)
                .param("word", word)
                .query((rs, n) -> new ResolvedLandmark(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional();
    }

    List<HotelCandidate> find(CandidateQuery q) {
        Map<String, Object> params = new HashMap<>();
        params.put("adults", q.adults());
        params.put("children", q.children());
        params.put("month", q.month());

        String mealFilter = "";
        if (q.mealPlan() != null) {
            mealFilter = "AND rr.meal_plan = :mealPlan";
            params.put("mealPlan", q.mealPlan().name());
        }
        String landmarkJoin = "";
        String landmarkDistance = "NULL::int";
        if (q.landmarkId() != null) {
            landmarkJoin = "JOIN landmark lm ON lm.id = :landmarkId";
            landmarkDistance = "ROUND(ST_Distance(h.location, lm.location))::int";
            params.put("landmarkId", q.landmarkId());
        }

        StringBuilder sql = new StringBuilder(
                SELECT.formatted(NEARBY_RADIUS_M, landmarkDistance, mealFilter, landmarkJoin));
        if (q.country() != null) {
            sql.append(" AND h.country = :country");
            params.put("country", q.country());
        }
        if (q.city() != null) {
            sql.append(" AND h.city = :city");
            params.put("city", q.city());
        }
        if (q.minStars() != null) {
            sql.append(" AND h.stars >= :minStars");
            params.put("minStars", q.minStars());
        }
        if (q.landmarkId() != null && q.radiusMeters() != null) {
            sql.append(" AND ST_DWithin(h.location, lm.location, :radius)");
            params.put("radius", q.radiusMeters());
        }
        sql.append(" ORDER BY h.rating DESC, h.id LIMIT :limit");
        params.put("limit", q.limit());

        return jdbc.sql(sql.toString()).params(params).query(ROW_MAPPER).list();
    }

    private static final RowMapper<HotelCandidate> ROW_MAPPER = (rs, rowNum) -> new HotelCandidate(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("country"),
            rs.getString("city"),
            rs.getString("district"),
            rs.getInt("stars"),
            rs.getBigDecimal("rating"),
            rs.getInt("reviews_count"),
            new GeoPoint(rs.getDouble("lat"), rs.getDouble("lon")),
            rs.getObject("beach_line", Integer.class),
            rs.getObject("distance_to_beach_m", Integer.class),
            rs.getObject("distance_to_center_m", Integer.class),
            rs.getObject("airport_transfer_min", Integer.class),
            rs.getBigDecimal("quiet_score"),
            rs.getBigDecimal("clean_score"),
            rs.getBigDecimal("wifi_score"),
            rs.getBigDecimal("family_score"),
            rs.getString("short_description"),
            amenities(rs),
            new HotelCandidate.Rate(
                    rs.getString("room_name"),
                    MealPlan.valueOf(rs.getString("meal_plan")),
                    rs.getBigDecimal("price_per_night"),
                    rs.getBoolean("refundable")),
            rs.getBigDecimal("season_coefficient"),
            rs.getObject("landmark_distance_m", Integer.class),
            nearby(rs));

    private static Set<Amenity> amenities(ResultSet rs) throws SQLException {
        Set<Amenity> result = EnumSet.noneOf(Amenity.class);
        for (String value : strings(rs.getArray("amenities"))) {
            try {
                result.add(Amenity.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // неизвестное удобство в данных не должно ломать поиск
            }
        }
        return result;
    }

    private static List<NearbyPlace> nearby(ResultSet rs) throws SQLException {
        List<NearbyPlace> result = new ArrayList<>();
        for (String value : strings(rs.getArray("nearby"))) {
            int sep = value.lastIndexOf('|');
            if (sep > 0) {
                int meters = Integer.parseInt(value.substring(sep + 1));
                result.add(new NearbyPlace(value.substring(0, sep), meters, WalkingTime.minutes(meters)));
            }
        }
        return result;
    }

    private static String[] strings(Array array) throws SQLException {
        if (array == null) {
            return new String[0];
        }
        try {
            return (String[]) array.getArray();
        } finally {
            array.free();
        }
    }
}
