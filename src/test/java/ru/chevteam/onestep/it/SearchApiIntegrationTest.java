package ru.chevteam.onestep.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.llm.provider=none")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SearchApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Турция | Хочу в Турцию на 10 дней в мае, всё включено, отель с аквапарком, двое взрослых и ребёнок 7 лет, до 80 тысяч. Лечу из Москвы, можно с пересадкой но лучше прямой. | Aqua Riviera Resort & Spa",
            "Сочи   | Ищу отель в Сочи на 7 дней в сентябре, 4 звезды, на первой линии, с завтраком, бюджет до 50 тысяч. Один еду. | Морской Бриз",
            "Питер  | Куда сходить в Питере на 2 ночи в выходные в январе? Центр, до Эрмитажа пешком минут 15, чисто, тихо, хороший Wi-Fi. До 8000 за двое суток. | Эрмитаж Бутик-Отель",
    })
    void demoScenarioFindsTheBestHotel(String scenario, String query, String expectedTop) throws Exception {
        JsonNode body = search(query);

        assertThat(body.at("/understood/source").asText()).isEqualTo("RULES");
        assertThat(body.at("/hotels/0/name").asText()).isEqualTo(expectedTop);
        assertThat(body.at("/hotels/0/matchPercent").asInt()).isGreaterThanOrEqualTo(90);
        assertThat(body.at("/hotels/0/reasons")).isNotEmpty();
        // все показанные варианты укладываются в бюджет с допуском 30%
        for (JsonNode hotel : body.get("hotels")) {
            assertThat(hotel.at("/price/total").decimalValue())
                    .isLessThanOrEqualTo(body.at("/understood/intent/budget/amount").decimalValue()
                            .multiply(new java.math.BigDecimal("1.3")));
        }
    }

    @Test
    void landmarkIsResolvedAndNearbyPlacesReturned() throws Exception {
        JsonNode body = search("Питер, 2 ночи, до Эрмитажа пешком 15 минут");

        assertThat(body.at("/understood/summary").toString()).contains("Эрмитаж");
        assertThat(body.at("/hotels/0/nearby/0/name").asText()).isNotBlank();
    }

    @Test
    void hotelCardIsAvailable() throws Exception {
        String id = search("Сочи, 4 звезды, первая линия").at("/hotels/0/hotelId").asText();

        mvc.perform(get("/api/v1/hotels/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates").isNotEmpty());
    }

    @Test
    void unknownHotelIsProblemDetail404() throws Exception {
        mvc.perform(get("/api/v1/hotels/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Ресурс не найден"));
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("query", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correlationIdIsEchoed() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "demo-123"))
                .andExpect(header().string("X-Correlation-Id", "demo-123"));
    }

    private JsonNode search(String query) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("query", query))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
