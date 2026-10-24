package ru.chevteam.onestep.search.internal;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.MealPlan;
import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.TransitKind;
import ru.chevteam.onestep.search.AccessibilityInfo;
import ru.chevteam.onestep.search.GeoPoint;
import ru.chevteam.onestep.search.HotelPolicies;
import ru.chevteam.onestep.search.HotelServices;
import ru.chevteam.onestep.search.NearbyPlace;
import ru.chevteam.onestep.search.Surroundings;
import ru.chevteam.onestep.search.Terrain;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Выборка кандидатов одним запросом: отель + самый дешёвый подходящий тариф (LATERAL),
 * сезонный коэффициент, расстояние до достопримечательности, ближайший пляж (PostGIS) и что интересного рядом.
 * <p>
 * SQL собирается из заранее заданных фрагментов; пользовательские значения — только через параметры.
 */
@Repository
class CandidateRepository {

    static final int NEARBY_RADIUS_M = 3000;
    static final int NEARBY_LIMIT = 8;
    static final int BEACH_RADIUS_M = 3000;
    static final int FOOD_RADIUS_M = 1500;
    static final int FOOD_LIMIT = 5;

    private static final String SELECT = """
            SELECT h.id, h.name, h.country, h.region, h.city, h.district, h.stars, h.rating, h.reviews_count,
                   ST_Y(h.location::geometry) AS lat, ST_X(h.location::geometry) AS lon,
                   h.beach_line, h.distance_to_beach_m, h.distance_to_center_m, h.airport_transfer_min,
                   h.quiet_score, h.clean_score, h.wifi_score, h.family_score, h.short_description,
                   h.step_free_access, h.accessible_room, h.elevator, h.terrain, h.accessibility_note,
                   nb.beach_id, nb.beach_distance_m,
                   to_char(h.check_in_from, 'HH24:MI') AS check_in_from, to_char(h.check_out_until, 'HH24:MI') AS check_out_until,
                   h.stay_note, h.pets_allowed, h.pet_fee, h.pet_note,
                   ARRAY(SELECT f.feature || '|' || f.available || '|' || COALESCE(f.note, '')
                           FROM hotel_feature f WHERE f.hotel_id = h.id ORDER BY f.feature) AS features,
                   r.id AS rate_id, r.room_name, r.meal_plan, r.price_per_night, r.refundable,
                   COALESCE(sc.coefficient, 1.0) AS season_coefficient,
                   ARRAY(SELECT a.amenity FROM hotel_amenity a WHERE a.hotel_id = h.id ORDER BY a.amenity) AS amenities,
                   ARRAY(SELECT l.name || '|' || l.kind || '|' || ROUND(ST_Distance(h.location, l.location))::int
                                   || '|' || COALESCE(l.note, '')
                           FROM landmark l
                          WHERE l.city = h.city AND ST_DWithin(h.location, l.location, %d)
                          ORDER BY ST_Distance(h.location, l.location)
                          LIMIT %d) AS nearby,
                   (SELECT l.name || '|' || l.kind || '|' || ROUND(ST_Distance(h.location, l.location))::int
                           || '|' || COALESCE(l.note, '')
                      FROM landmark l
                     WHERE l.city = h.city AND l.kind = 'SHOPPING' AND ST_DWithin(h.location, l.location, 3000)
                     ORDER BY ST_Distance(h.location, l.location)
                     LIMIT 1) AS shopping,
                   h.road_noise, h.road_note, h.car_access, h.access_note,
                   ARRAY(SELECT t.kind || '|' || t.name || '|' || t.distance_m || '|' || t.minutes || '|' || t.mode
                                   || '|' || COALESCE(t.note, '')
                           FROM hotel_transport t WHERE t.hotel_id = h.id ORDER BY t.kind, t.minutes) AS transport,
                   ARRAY(SELECT hf.facility || '|' || hf.available || '|' || COALESCE(hf.note, '')
                           FROM hotel_facility hf WHERE hf.hotel_id = h.id ORDER BY hf.facility) AS facilities,
                   hd.hotel_id AS dining_hotel, hd.cuisines AS dining_cuisines, hd.diets AS dining_diets, hd.note AS dining_note,
                   ARRAY(SELECT ma.meal_plan || '|' || ma.adult_price || '|' || ma.child_price || '|' || ma.child_free_under
                                   || '|' || COALESCE(ma.note, '')
                           FROM meal_addon ma WHERE ma.hotel_id = h.id ORDER BY ma.adult_price) AS meal_addons,
                   ARRAY(SELECT fp.name || '|' || ROUND(ST_Distance(h.location, fp.location))::int || '|'
                                   || array_to_string(fp.meals, ',') || '|' || array_to_string(fp.diets, ',') || '|'
                                   || fp.cuisine || '|' || fp.price_level || '|' || COALESCE(fp.note, '')
                           FROM food_place fp
                          WHERE fp.city = h.city AND ST_DWithin(h.location, fp.location, %d)
                          ORDER BY ST_Distance(h.location, fp.location)
                          LIMIT %d) AS food_nearby,
                   %s AS landmark_distance_m
              FROM hotel h
              JOIN LATERAL (
                    SELECT rr.id, rr.room_name, rr.meal_plan, rr.price_per_night, rr.refundable
                      FROM room_rate rr
                     WHERE rr.hotel_id = h.id
                       AND rr.max_adults >= :adults
                       AND rr.max_children >= :children
                       %s
                     ORDER BY %s rr.price_per_night
                     LIMIT 1
                   ) r ON TRUE
              LEFT JOIN LATERAL (
                    SELECT b.id AS beach_id, ROUND(ST_Distance(h.location, b.location))::int AS beach_distance_m
                      FROM beach b
                     WHERE b.city = h.city AND ST_DWithin(h.location, b.location, %d)
                     ORDER BY ST_Distance(h.location, b.location)
                     LIMIT 1
                   ) nb ON TRUE
              LEFT JOIN season_coefficient sc ON sc.city = h.city AND sc.month = :month
              LEFT JOIN hotel_dining hd ON hd.hotel_id = h.id
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
        String mealOrder = "";
        if (q.mealPlan() != null) {
            // подходит тариф с этим питанием или любой тариф отеля, к которому питание можно добавить
            mealFilter = "AND (rr.meal_plan = :mealPlan OR EXISTS (SELECT 1 FROM meal_addon ma "
                    + "WHERE ma.hotel_id = rr.hotel_id AND ma.meal_plan = :mealPlan))";
            mealOrder = "(rr.meal_plan = :mealPlan) DESC,";
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
                SELECT.formatted(NEARBY_RADIUS_M, NEARBY_LIMIT, FOOD_RADIUS_M, FOOD_LIMIT, landmarkDistance, mealFilter,
                        mealOrder, BEACH_RADIUS_M, landmarkJoin));
        if (q.country() != null) {
            sql.append(" AND h.country = :country");
            params.put("country", q.country());
        }
        if (q.region() != null) {
            sql.append(" AND h.region = :region");
            params.put("region", q.region());
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
            rs.getString("region"),
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
                    rs.getObject("rate_id", UUID.class),
                    rs.getString("room_name"),
                    MealPlan.valueOf(rs.getString("meal_plan")),
                    rs.getBigDecimal("price_per_night"),
                    rs.getBoolean("refundable")),
            rs.getBigDecimal("season_coefficient"),
            rs.getObject("landmark_distance_m", Integer.class),
            nearby(rs),
            new AccessibilityInfo(rs.getBoolean("step_free_access"), rs.getBoolean("accessible_room"),
                    rs.getBoolean("elevator"), Terrain.valueOf(rs.getString("terrain")), rs.getString("accessibility_note")),
            rs.getObject("beach_id", UUID.class),
            rs.getObject("beach_distance_m", Integer.class),
            policies(rs),
            new Surroundings(Surroundings.Level.valueOf(rs.getString("road_noise")), rs.getString("road_note"),
                    Surroundings.CarAccess.valueOf(rs.getString("car_access")), rs.getString("access_note")),
            place(rs.getString("shopping")),
            services(rs));

    private static HotelServices services(ResultSet rs) throws SQLException {
        List<HotelServices.TransitPoint> transport = new ArrayList<>();
        for (String value : strings(rs.getArray("transport"))) {
            String[] p = value.split("\\|", 6);
            try {
                transport.add(new HotelServices.TransitPoint(TransitKind.valueOf(p[0]), p[1], Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), HotelServices.TransitPoint.Mode.valueOf(p[4]), blank(p, 5)));
            } catch (RuntimeException ignored) {
                // неполная строка в данных не должна ломать поиск
            }
        }
        List<HotelServices.Facility> facilities = new ArrayList<>();
        for (String value : strings(rs.getArray("facilities"))) {
            String[] p = value.split("\\|", 3);
            try {
                facilities.add(new HotelServices.Facility(Amenity.valueOf(p[0]), "true".equals(p[1]) || "t".equals(p[1]), blank(p, 2)));
            } catch (RuntimeException ignored) {
                // аналогично
            }
        }
        HotelServices.Dining dining = rs.getObject("dining_hotel") == null ? null : new HotelServices.Dining(
                List.of(strings(rs.getArray("dining_cuisines"))), enums(DietNeed.class, strings(rs.getArray("dining_diets"))),
                rs.getString("dining_note"));
        List<HotelServices.MealAddon> addons = new ArrayList<>();
        for (String value : strings(rs.getArray("meal_addons"))) {
            String[] p = value.split("\\|", 5);
            try {
                addons.add(new HotelServices.MealAddon(MealPlan.valueOf(p[0]), new BigDecimal(p[1]), new BigDecimal(p[2]),
                        Integer.parseInt(p[3]), blank(p, 4)));
            } catch (RuntimeException ignored) {
                // аналогично
            }
        }
        List<HotelServices.FoodPlace> food = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : strings(rs.getArray("food_nearby"))) {
            String[] p = value.split("\\|", 7);
            if (!seen.add(p[0])) {
                continue; // сетевое кафе рядом с соседним отелем — показываем ближайшее
            }
            try {
                int meters = Integer.parseInt(p[1]);
                food.add(new HotelServices.FoodPlace(p[0], meters, WalkingTime.minutes(meters),
                        enums(HotelServices.FoodPlace.Meal.class, p[2].split(",")), enums(DietNeed.class, p[3].split(",")),
                        p[4], Integer.parseInt(p[5]), blank(p, 6)));
            } catch (RuntimeException ignored) {
                // аналогично
            }
        }
        return new HotelServices(transport, facilities, dining, addons, food);
    }

    private static String blank(String[] parts, int i) {
        return parts.length > i && !parts[i].isBlank() ? parts[i] : null;
    }

    private static <E extends Enum<E>> Set<E> enums(Class<E> type, String[] values) {
        Set<E> result = EnumSet.noneOf(type);
        for (String value : values) {
            try {
                result.add(Enum.valueOf(type, value.strip()));
            } catch (IllegalArgumentException ignored) {
                // пустые и неизвестные значения пропускаем
            }
        }
        return result;
    }

    private static HotelPolicies policies(ResultSet rs) throws SQLException {
        Set<PetKind> pets = EnumSet.noneOf(PetKind.class);
        for (String value : strings(rs.getArray("pets_allowed"))) {
            try {
                pets.add(PetKind.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // неизвестный вид в данных не должен ломать поиск
            }
        }
        List<HotelPolicies.Feature> features = new ArrayList<>();
        for (String value : strings(rs.getArray("features"))) {
            String[] parts = value.split("\\|", 3);
            try {
                features.add(new HotelPolicies.Feature(RoomFeature.valueOf(parts[0]), "true".equals(parts[1]) || "t".equals(parts[1]),
                        parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null));
            } catch (IllegalArgumentException ignored) {
                // аналогично
            }
        }
        return new HotelPolicies(rs.getString("check_in_from"), rs.getString("check_out_until"), rs.getString("stay_note"),
                new HotelPolicies.Pets(pets, rs.getBigDecimal("pet_fee"), rs.getString("pet_note")), features);
    }

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
            NearbyPlace place = place(value);
            if (place != null) {
                result.add(place);
            }
        }
        return result;
    }

    /** «имя|тип|метры|подсказка» → место. Имя не содержит «|», подсказка может быть пустой. */
    static NearbyPlace place(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", 4);
        if (parts.length < 3) {
            return null;
        }
        int meters = Integer.parseInt(parts[2]);
        String note = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
        return new NearbyPlace(parts[0], parts[1], meters, WalkingTime.minutes(meters), WalkingTime.slowMinutes(meters), note);
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
