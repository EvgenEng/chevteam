package ru.chevteam.onestep.metrics.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ru.chevteam.onestep.metrics.GuestEventType;
import ru.chevteam.onestep.metrics.TimeToChoice;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * События гостя и расчёт «времени до выбора». Время ставит сервер, а не браузер: подделать длительность нельзя.
 */
@Service
public class GuestMetrics {

    /** Сессию длиннее этого не считаем одной попыткой найти отель — гость ушёл и вернулся. */
    static final Duration MAX_SESSION = Duration.ofHours(2);
    private static final int MAX_EVENTS_PER_SESSION = 300;

    private final JdbcClient jdbc;
    private final Clock clock;

    GuestMetrics(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(UUID session, GuestEventType type, Integer rank, Integer results) {
        int already = jdbc.sql("SELECT COUNT(*) FROM guest_event WHERE session_id = :s").param("s", session)
                .query(Integer.class).single();
        if (already >= MAX_EVENTS_PER_SESSION) {
            return; // не даём одной вкладке раздуть таблицу
        }
        jdbc.sql("INSERT INTO guest_event (session_id, type, at, hotel_rank, results) VALUES (:s, :t, :at, :rank, :results)")
                .param("s", session).param("t", type.name()).param("at", Timestamp.from(clock.instant()))
                .param("rank", rank).param("results", results)
                .update();
    }

    public TimeToChoice summary() {
        Instant since = clock.instant().minus(Duration.ofDays(30));
        List<Event> events = jdbc.sql("""
                        SELECT session_id, type, at, hotel_rank FROM guest_event
                         WHERE at >= :since ORDER BY session_id, at, id LIMIT 50000
                        """)
                .param("since", Timestamp.from(since))
                .query((rs, n) -> new Event(rs.getObject("session_id", UUID.class), GuestEventType.valueOf(rs.getString("type")),
                        rs.getTimestamp("at").toInstant(), (Integer) rs.getObject("hotel_rank")))
                .list();
        return summarize(events);
    }

    public void reset() {
        jdbc.sql("DELETE FROM guest_event").update();
    }

    /** Чистый расчёт — отдельно от базы, чтобы проверять тестом. */
    static TimeToChoice summarize(List<Event> events) {
        Map<UUID, List<Event>> bySession = new LinkedHashMap<>();
        for (Event e : events) {
            bySession.computeIfAbsent(e.session(), k -> new ArrayList<>()).add(e);
        }
        List<TimeToChoice.Session> sessions = new ArrayList<>();
        for (List<Event> list : bySession.values()) {
            list.sort(Comparator.comparing(Event::at));
            Instant start = null;
            int queries = 0;
            boolean compared = false;
            Long seconds = null;
            Integer rank = null;
            boolean booked = false;
            for (Event e : list) {
                switch (e.type()) {
                    case SEARCH -> {
                        if (start == null) {
                            start = e.at();
                        }
                        if (seconds == null) {
                            queries++;
                        }
                    }
                    case COMPARE -> compared |= seconds == null && start != null;
                    case CHOOSE -> {
                        if (start != null && seconds == null && Duration.between(start, e.at()).compareTo(MAX_SESSION) <= 0) {
                            seconds = Duration.between(start, e.at()).toSeconds();
                            rank = e.rank();
                        }
                    }
                    case BOOKED -> booked |= seconds != null;
                }
            }
            if (start != null) {
                sessions.add(new TimeToChoice.Session(start, queries, seconds, rank, seconds != null && compared, booked));
            }
        }
        List<TimeToChoice.Session> chose = sessions.stream().filter(s -> s.seconds() != null).toList();
        List<TimeToChoice.Session> recent = sessions.stream()
                .sorted(Comparator.comparing(TimeToChoice.Session::startedAt).reversed()).limit(10).toList();
        return new TimeToChoice(sessions.size(), chose.size(), (int) sessions.stream().filter(TimeToChoice.Session::booked).count(),
                median(chose.stream().map(s -> (double) s.seconds()).toList()),
                median(chose.stream().map(s -> (double) s.queries()).toList()),
                share(chose, s -> s.rank() != null && s.rank() <= 3),
                share(chose, TimeToChoice.Session::compared),
                recent);
    }

    private static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static Double share(List<TimeToChoice.Session> list, java.util.function.Predicate<TimeToChoice.Session> p) {
        return list.isEmpty() ? null : Math.round(100.0 * list.stream().filter(p).count() / list.size()) / 100.0;
    }

    record Event(UUID session, GuestEventType type, Instant at, Integer rank) {
    }
}
