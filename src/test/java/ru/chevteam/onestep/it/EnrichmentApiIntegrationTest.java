package ru.chevteam.onestep.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.chevteam.onestep.enrichment.OsmSourceFetcher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Пайплайн на настоящем PostGIS: снимок OSM → факты → поиск → сверка заявления отельера. */
@SpringBootTest(properties = {"app.llm.provider=none", "app.enrichment.run-on-startup=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, EnrichmentApiIntegrationTest.FakeOverpass.class})
class EnrichmentApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void runPipeline() throws Exception {
        JsonNode run = call(post("/api/v1/admin/enrichment/run"), 200);
        assertThat(run.get("hotels").asInt()).isGreaterThanOrEqualTo(20);
        assertThat(run.get("facts").asInt()).isGreaterThan(200);
    }

    UUID hotel(String name) {
        return jdbc.sql("SELECT id FROM hotel WHERE name = :name AND data_source = 'OSM'").param("name", name)
                .query(UUID.class).single();
    }

    @Test
    void factsHaveSourceMethodAndEvidence() throws Exception {
        JsonNode profile = call(get("/api/v1/hotels/" + hotel("Бристоль") + "/facts"), 200);
        JsonNode noise = null;
        for (JsonNode f : profile.get("facts")) {
            assertThat(f.get("source").asText()).isEqualTo("OSM");
            assertThat(f.get("method").asText()).isNotBlank();
            if ("ROAD_NOISE".equals(f.get("code").asText())) {
                noise = f;
            }
        }
        assertThat(noise).isNotNull();
        assertThat(noise.get("level").asText()).isEqualTo("HIGH");
        assertThat(noise.get("evidence").asText()).contains("Рузвельта");
    }

    @Test
    void hotelierClaimIsCheckedAgainstTheMap() throws Exception {
        JsonNode check = call(put("/api/v1/extranet/hotels/" + hotel("Бристоль") + "/claims/ROAD_NOISE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"LOW\",\"author\":\"тест\"}"), 200);
        assertThat(check.get("status").asText()).isEqualTo("DISPUTED");

        JsonNode profile = call(get("/api/v1/hotels/" + hotel("Бристоль") + "/facts"), 200);
        assertThat(profile.at("/checks/0/status").asText()).isEqualTo("DISPUTED");
        assertThat(profile.get("trust").asDouble()).isZero();

        // в выдаче по Ялте у реальных отелей есть проверенные по карте факты
        JsonNode search = call(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("query", "Ялта у моря, 3 ночи, проверка сверки"))), 200);
        assertThat(search.get("hotels")).isNotEmpty().allSatisfy(h -> {
            assertThat(h.get("dataSource").asText()).isEqualTo("OSM");
            assertThat(h.at("/verified/facts")).isNotEmpty();
        });
    }

    @Test
    void hotelierTasksLeadTheCardToVerified() throws Exception {
        UUID inturist = hotel("Ялта-Интурист");
        String tasks = "/api/v1/extranet/hotels/" + inturist + "/tasks";
        JsonNode board = call(get(tasks), 200);
        assertThat(board.at("/score/level").asText()).isEqualTo("NEW");
        assertThat(board.get("tasks")).hasSize(9);

        // заявили «без лестниц», а по карте ≈230 м лестниц — задача «исправьте» с вариантом карты
        call(put("/api/v1/extranet/hotels/" + inturist + "/claims/STAIRS_TO_SEA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":\"NONE\"}"), 200);
        board = call(get(tasks), 200);
        JsonNode fix = board.get("tasks").get(0);
        assertThat(fix.get("kind").asText()).isEqualTo("FIX");
        assertThat(fix.get("code").asText()).isEqualTo("STAIRS_TO_SEA");
        assertThat(fix.get("suggestedLevel").asText()).isEqualTo("MANY");

        // отельер принимает всё, что предлагает карта, — карточка становится «Проверенной»
        for (JsonNode task : board.get("tasks")) {
            if (!task.path("suggestedLevel").isMissingNode() && !task.get("suggestedLevel").isNull()) {
                call(put("/api/v1/extranet/hotels/" + inturist + "/claims/" + task.get("code").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"" + task.get("suggestedLevel").asText() + "\"}"), 200);
            }
        }
        board = call(get(tasks), 200);
        assertThat(board.at("/score/disputes").asInt()).isZero();
        assertThat(board.at("/score/level").asText()).isEqualTo("VERIFIED");
        assertThat(call(get("/api/v1/hotels/" + inturist + "/facts"), 200).at("/card/level").asText()).isEqualTo("VERIFIED");
    }

    @Test
    void characteristicAddedAsText() throws Exception {
        JsonNode added = call(post("/api/v1/admin/characteristics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"аптека в 5 минутах\"}"), 201);
        assertThat(added.at("/recipe/filter").asText()).isEqualTo("amenity=pharmacy");
        assertThat(added.get("hotelsWithYes").asInt()).isPositive();
        call(post("/api/v1/admin/characteristics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"красивый вид на закат\"}"), 422);
    }

    @Test
    void fictionalHotelsOfTheRegionAreHidden() throws Exception {
        JsonNode search = call(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("query", "Алушта у моря на 5 ночей"))), 200);
        assertThat(search.get("hotels")).isNotEmpty()
                .allSatisfy(h -> assertThat(h.get("dataSource").asText()).isEqualTo("OSM"));
    }

    @Test
    void dataAgentAsksBeforeFetchingObjectsTheSnapshotLacks() throws Exception {
        JsonNode added = call(post("/api/v1/admin/characteristics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"детская площадка рядом\"}"), 202);
        assertThat(added.at("/dataRequest/status").asText()).isEqualTo("PENDING");
        assertThat(added.at("/dataRequest/filter").asText()).isEqualTo("leisure=playground");
        assertThat(added.at("/dataRequest/query").asText()).contains("nwr[\"leisure\"=\"playground\"]");
        String code = added.at("/recipe/code").asText();
        String id = added.at("/dataRequest/id").asText();

        // пока админ не решил — фактов нет: «площадки рядом нет» было бы неправдой
        assertThat(codes(hotel("Бристоль"))).doesNotContain(code);

        JsonNode decision = call(post("/api/v1/admin/sources/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"by\":\"тест\"}"), 200);
        assertThat(decision.at("/request/status").asText()).isEqualTo("FETCHED");
        assertThat(decision.at("/request/elements").asInt()).isEqualTo(1);
        assertThat(decision.get("characteristics").toString()).contains("Детская площадка");

        JsonNode fact = null;
        for (JsonNode f : call(get("/api/v1/hotels/" + hotel("Бристоль") + "/facts"), 200).get("facts")) {
            if (code.equals(f.get("code").asText())) {
                fact = f;
            }
        }
        assertThat(fact).isNotNull();
        assertThat(fact.get("level").asText()).isEqualTo("YES");
        assertThat(fact.get("evidence").asText()).contains("Площадка у набережной");

        call(post("/api/v1/admin/sources/" + id + "/approve"), 422);
    }

    @Test
    void rejectedRequestDownloadsNothingAndDropsTheCharacteristic() throws Exception {
        JsonNode added = call(post("/api/v1/admin/characteristics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"заправка в 2 км\"}"), 202);
        String code = added.at("/recipe/code").asText();
        JsonNode decision = call(post("/api/v1/admin/sources/" + added.at("/dataRequest/id").asText() + "/reject"), 200);
        assertThat(decision.at("/request/status").asText()).isEqualTo("REJECTED");
        assertThat(call(get("/api/v1/admin/enrichment"), 200).get("custom").toString()).doesNotContain(code);
    }

    private List<String> codes(UUID hotelId) throws Exception {
        List<String> result = new java.util.ArrayList<>();
        call(get("/api/v1/hotels/" + hotelId + "/facts"), 200).get("facts").forEach(f -> result.add(f.get("code").asText()));
        return result;
    }

    /** Вместо публичного Overpass — одна детская площадка у Бристоля: тест не ходит в интернет. */
    @TestConfiguration
    static class FakeOverpass {

        @Bean
        @Primary
        OsmSourceFetcher fakeOverpass() {
            return query -> new OsmSourceFetcher.Fetched("""
                    {"elements":[{"type":"node","id":1,"lat":44.4968,"lon":34.1722,
                      "tags":{"leisure":"playground","name":"Площадка у набережной"}}]}""", "test");
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        return body.length == 0 ? json.nullNode() : json.readTree(body);
    }
}
