package ru.chevteam.onestep.reviews.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chevteam.onestep.reviews.Review;
import ru.chevteam.onestep.reviews.ReviewPage;
import ru.chevteam.onestep.reviews.ReviewSort;
import ru.chevteam.onestep.reviews.ReviewSummary;
import ru.chevteam.onestep.reviews.Reviews;
import ru.chevteam.onestep.reviews.TripType;
import ru.chevteam.onestep.shared.error.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Чтение отзывов. Имена колонок и порядок сортировки берутся только из перечислений —
 * пользовательский ввод попадает в SQL исключительно параметрами.
 */
@Service
class JdbcReviews implements Reviews {

    static final int MAX_LIMIT = 50;
    static final int ASPECTS = 5;

    private final JdbcClient jdbc;

    JdbcReviews(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage find(Subject subject, UUID subjectId, TripType tripType, ReviewSort sort, int limit, int offset) {
        String column = switch (subject) {
            case HOTEL -> "hotel_id";
            case BEACH -> "beach_id";
        };
        String table = switch (subject) {
            case HOTEL -> "hotel";
            case BEACH -> "beach";
        };
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)")
                .param("id", subjectId).query(Boolean.class).single();
        if (!exists) {
            throw new NotFoundException((subject == Subject.HOTEL ? "Отель " : "Пляж ") + subjectId + " не найден");
        }
        int pageSize = Math.max(1, Math.min(MAX_LIMIT, limit));
        int skip = Math.max(0, offset);

        Map<String, Object> params = new HashMap<>();
        params.put("id", subjectId);
        String filter = " WHERE r." + column + " = :id";
        if (tripType != null) {
            filter += " AND r.trip_type = :tripType";
            params.put("tripType", tripType.name());
        }

        Map<TripType, Integer> byTripType = new EnumMap<>(TripType.class);
        jdbc.sql("SELECT trip_type, count(*) AS n FROM review r WHERE r." + column + " = :id GROUP BY trip_type")
                .param("id", subjectId)
                .query((rs, n) -> Map.entry(TripType.valueOf(rs.getString("trip_type")), rs.getInt("n")))
                .list().forEach(e -> byTripType.put(e.getKey(), e.getValue()));

        Object[] totals = jdbc.sql("SELECT count(*) AS n, avg(r.rating) AS avg FROM review r" + filter)
                .params(params)
                .query((rs, n) -> new Object[]{rs.getInt("n"), rs.getBigDecimal("avg")})
                .single();
        int total = (int) totals[0];
        BigDecimal average = totals[1] == null ? null : ((BigDecimal) totals[1]).setScale(1, RoundingMode.HALF_UP);

        List<ReviewSummary.Aspect> praised = new ArrayList<>();
        List<ReviewSummary.Aspect> criticized = new ArrayList<>();
        jdbc.sql("SELECT tag, count(*) AS n FROM review r, unnest(r.tags) AS tag" + filter
                        + " GROUP BY tag ORDER BY n DESC, tag")
                .params(params)
                .query((rs, n) -> Map.entry(rs.getString("tag"), rs.getInt("n")))
                .list().forEach(e -> {
                    String tag = e.getKey();
                    if (tag.length() < 2) {
                        return;
                    }
                    List<ReviewSummary.Aspect> sink = tag.charAt(0) == '+' ? praised : tag.charAt(0) == '-' ? criticized : null;
                    if (sink != null && sink.size() < ASPECTS) {
                        sink.add(new ReviewSummary.Aspect(tag.substring(1), e.getValue()));
                    }
                });

        String order = switch (sort == null ? ReviewSort.NEWEST : sort) {
            case NEWEST -> "r.created_at DESC, r.id";
            case BEST -> "r.rating DESC, r.created_at DESC, r.id";
            case WORST -> "r.rating ASC, r.created_at DESC, r.id";
        };
        params.put("limit", pageSize);
        params.put("offset", skip);
        List<Review> items = jdbc.sql("SELECT r.* FROM review r" + filter + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new Review(
                        rs.getObject("id", UUID.class),
                        rs.getString("author"),
                        TripType.valueOf(rs.getString("trip_type")),
                        rs.getBigDecimal("rating"),
                        rs.getDate("stayed_on").toLocalDate(),
                        rs.getString("title"),
                        rs.getString("pros"),
                        rs.getString("cons"),
                        List.of(strings(rs.getArray("tags"))),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();

        ReviewSummary summary = new ReviewSummary(total, average, byTripType, praised, criticized);
        return new ReviewPage(summary, items, total, pageSize, skip, true);
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
