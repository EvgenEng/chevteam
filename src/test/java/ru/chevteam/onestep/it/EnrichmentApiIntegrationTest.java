package ru.chevteam.onestep.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
@Import(TestcontainersConfiguration.class)
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

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        return body.length == 0 ? json.nullNode() : json.readTree(body);
    }
}
