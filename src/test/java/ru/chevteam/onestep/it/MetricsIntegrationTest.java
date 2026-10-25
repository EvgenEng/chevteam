package ru.chevteam.onestep.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Та же конфигурация, что у тестов поиска, — Spring переиспользует контекст и контейнеры. */
@SpringBootTest(properties = "app.llm.provider=none")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MetricsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void guestStepsBecomeTimeToChoice() throws Exception {
        mvc.perform(delete("/api/v1/admin/metrics")).andExpect(status().isNoContent());
        UUID session = UUID.randomUUID();
        event(session, "{\"type\":\"SEARCH\",\"results\":5}");
        event(session, "{\"type\":\"COMPARE\"}");
        event(session, "{\"type\":\"CHOOSE\",\"rank\":1}");

        JsonNode m = json.readTree(mvc.perform(get("/api/v1/admin/metrics")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(m.get("sessions").asInt()).isEqualTo(1);
        assertThat(m.get("chose").asInt()).isEqualTo(1);
        assertThat(m.get("medianQueries").asDouble()).isEqualTo(1.0);
        assertThat(m.get("fromTop3").asDouble()).isEqualTo(1.0);
        assertThat(m.at("/recent/0/compared").asBoolean()).isTrue();

        mvc.perform(post("/api/v1/metrics/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + session + "\",\"type\":\"HACK\"}")).andExpect(status().isBadRequest());
    }

    private void event(UUID session, String body) throws Exception {
        mvc.perform(post("/api/v1/metrics/events").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("{", "{\"sessionId\":\"" + session + "\","))).andExpect(status().isNoContent());
    }
}
