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
    void thailandFindsPhuket() throws Exception {
        JsonNode body = search("Хочу в Тайланд на 7 дней, двое взрослых, до 100 тысяч. Лечу из Москвы, лучше прямой.");

        assertThat(body.get("notice").isNull()).isTrue();
        assertThat(body.at("/hotels/0/city").asText()).isIn("Пхукет", "Паттайя");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Япония в апреле на 5 ночей, двое взрослых                      | Япония",
            "Сингапур, 3 ночи, до Марина Бэй Сэндс пешком 10 минут          | Сингапур",
            "Хайнань в январе, всё включено, аквапарк, двое взрослых и ребёнок 6 лет | Китай",
            "Камбоджа, Сием-Рип, 3 ночи, тихо                               | Камбоджа",
    })
    void asianDestinations(String query, String country) throws Exception {
        JsonNode body = search(query);
        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/understood/intent/destination/country").asText()).isEqualTo(country);
        assertThat(body.get("hotels")).isNotEmpty();
    }

    @Test
    void multiWordLandmarkIsResolved() throws Exception {
        JsonNode body = search("Сингапур, 3 ночи, до Марина Бэй Сэндс пешком 10 минут");
        assertThat(body.at("/understood/summary").toString()).contains("Марина Бэй Сэндс");
        assertThat(body.at("/hotels/0/name").asText()).isEqualTo("Marina Bay Vista");
    }

    @Test
    void cityWithoutHotelsFallsBackToCountry() throws Exception {
        JsonNode body = search("Гуанчжоу на 4 ночи, двое взрослых");
        assertThat(body.get("notice").asText()).contains("Гуанчжоу").contains("Китай");
        assertThat(body.at("/hotels/0/city").asText()).isIn("Пекин", "Шанхай", "Санья");
    }

    @Test
    void destinationWithoutHotelsIsHonestAndOffersAlternatives() throws Exception {
        JsonNode body = search("На Мальдивы вдвоём на неделю, до 300 тысяч");

        assertThat(body.get("notice").asText()).contains("Мальдивы");
        assertThat(body.get("hotels")).isNotEmpty();
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
