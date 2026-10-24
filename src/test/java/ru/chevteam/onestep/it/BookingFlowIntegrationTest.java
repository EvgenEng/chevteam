package ru.chevteam.onestep.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.chevteam.onestep.booking.BookingEvents;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Полный путь: поиск → удержание → отказ оплаты → оплата → событие в Kafka → отмена.
 * Плюс гарантии: идемпотентность и отсутствие овербукинга при гонке за последний номер.
 */
@SpringBootTest(properties = "app.llm.provider=none")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingFlowIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2027, 5, 10);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaConnectionDetails kafka;

    @Test
    void fullBookingJourney() throws Exception {
        // бюджет отсекает невозвратный Belek Grand Lagoon — в топе возвратный тариф
        UUID rateId = topRateFor("Турция, всё включено, аквапарк, двое взрослых и ребёнок 7 лет, 9 ночей в мае, до 80 тысяч");
        assertThat(refundable(rateId)).isTrue();

        JsonNode held = hold(rateId, CHECK_IN, 9, "journey-hold-0001", 201);
        String bookingId = held.get("id").asText();
        assertThat(held.get("status").asText()).isEqualTo("HELD");
        assertThat(held.get("reference").asText()).startsWith("OS-");
        assertThat(held.get("total").decimalValue()).isPositive();

        // отказ банка: 402, бронь остаётся удержанной
        MvcResult declined = pay(bookingId, "TEST_CARD_DECLINE", "journey-pay-0001");
        assertThat(declined.getResponse().getStatus()).isEqualTo(402);
        assertThat(read(declined).at("/booking/status").asText()).isEqualTo("HELD");

        // успешная оплата новым ключом
        MvcResult paid = pay(bookingId, "TEST_CARD_SUCCESS", "journey-pay-0002");
        assertThat(paid.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(paid).get("status").asText()).isEqualTo("CONFIRMED");

        // повтор того же запроса оплаты — сохранённый ответ, без второго списания
        MvcResult replay = pay(bookingId, "TEST_CARD_SUCCESS", "journey-pay-0002");
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");

        // событие дошло до Kafka через outbox
        String reference = held.get("reference").asText();
        assertThat(consumeBookingEventKeys()).contains(reference);

        // отмена возвращает номер в продажу
        MvcResult cancel = mvc.perform(delete("/api/v1/bookings/{id}", bookingId)).andReturn();
        assertThat(cancel.getResponse().getStatus()).as(cancel.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode cancelled = read(mvc.perform(get("/api/v1/bookings/{id}", bookingId)).andReturn());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(booked(rateId, CHECK_IN)).isZero();
    }

    @Test
    void nonRefundableConfirmedBookingCannotBeCancelled() throws Exception {
        UUID rateId = jdbc.sql("SELECT id FROM room_rate WHERE NOT refundable ORDER BY id LIMIT 1")
                .query(UUID.class).single();
        LocalDate checkIn = LocalDate.of(2027, 6, 1);
        String bookingId = hold(rateId, checkIn, 3, "nonref-hold-0001", 201).get("id").asText();
        assertThat(pay(bookingId, "TEST_CARD_SUCCESS", "nonref-pay-0001").getResponse().getStatus()).isEqualTo(200);

        MvcResult cancel = mvc.perform(delete("/api/v1/bookings/{id}", bookingId)).andReturn();

        assertThat(cancel.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(cancel).get("detail").asText()).contains("невозвратный");
        assertThat(booked(rateId, checkIn)).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyDoesNotCreateSecondBooking() throws Exception {
        UUID rateId = topRateFor("Сочи, 4 звезды, первая линия, с завтраком, один еду");
        LocalDate checkIn = LocalDate.of(2027, 9, 1);

        JsonNode first = hold(rateId, checkIn, 2, "idem-hold-0001", 201);
        JsonNode second = hold(rateId, checkIn, 2, "idem-hold-0001", 201);

        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(booked(rateId, checkIn)).isEqualTo(1);
    }

    @Test
    void parallelHoldsNeverOverbook() throws Exception {
        UUID rateId = topRateFor("Питер, 2 ночи, до Эрмитажа пешком 15 минут, тихо");
        LocalDate checkIn = LocalDate.of(2027, 2, 12);
        int allotment = jdbc.sql("SELECT allotment FROM room_rate WHERE id = :id").param("id", rateId)
                .query(Integer.class).single();

        int attempts = 10;
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String key = "race-hold-" + String.format("%04d", i);
            calls.add(() -> holdStatus(rateId, checkIn, 2, key));
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(allotment);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - allotment);
        assertThat(booked(rateId, checkIn)).isEqualTo(allotment);
    }

    @Test
    void capacityAndKeyAreValidated() throws Exception {
        UUID rateId = topRateFor("Питер, 2 ночи, до Эрмитажа пешком 15 минут");
        // номер на двоих, а гостей пятеро
        MvcResult tooMany = mvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "capacity-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(rateId, CHECK_IN, 2, 5))))
                .andReturn();
        assertThat(tooMany.getResponse().getStatus()).isEqualTo(422);

        MvcResult noKey = mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(rateId, CHECK_IN, 2, 2))))
                .andReturn();
        assertThat(noKey.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ helpers

    private UUID topRateFor(String query) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("query", query)))).andReturn();
        return UUID.fromString(read(result).at("/hotels/0/rateId").asText());
    }

    private JsonNode hold(UUID rateId, LocalDate checkIn, int nights, String key, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(rateId, checkIn, nights, 1))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return read(result);
    }

    private int holdStatus(UUID rateId, LocalDate checkIn, int nights, String key) throws Exception {
        return mvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(holdBody(rateId, checkIn, nights, 1))))
                .andReturn().getResponse().getStatus();
    }

    private Map<String, Object> holdBody(UUID rateId, LocalDate checkIn, int nights, int adults) {
        return Map.of("rateId", rateId, "checkIn", checkIn.toString(), "nights", nights, "adults", adults,
                "childrenAges", List.of(), "guestName", "Тестовый Гость", "guestEmail", "guest@example.com");
    }

    private MvcResult pay(String bookingId, String method, String key) throws Exception {
        return mvc.perform(post("/api/v1/bookings/{id}/payment", bookingId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("method", method))))
                .andReturn();
    }

    private boolean refundable(UUID rateId) {
        return jdbc.sql("SELECT refundable FROM room_rate WHERE id = :id").param("id", rateId)
                .query(Boolean.class).single();
    }

    private int booked(UUID rateId, LocalDate day) {
        return jdbc.sql("SELECT COALESCE(SUM(booked), 0) FROM room_inventory WHERE rate_id = :id AND stay_date = :day")
                .param("id", rateId).param("day", day).query(Integer.class).single();
    }

    private List<String> consumeBookingEventKeys() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(BookingEvents.TOPIC));
            List<String> keys = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (keys.isEmpty() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                    keys.add(record.key());
                }
            }
            return keys;
        }
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
