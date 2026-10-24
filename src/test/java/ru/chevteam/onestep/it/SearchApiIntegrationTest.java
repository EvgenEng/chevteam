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
    void thailandStaysInThailand() throws Exception {
        JsonNode body = search("Хочу в Тайланд на 7 дней, двое взрослых, до 100 тысяч. Лечу из Москвы, лучше прямой.");

        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/understood/intent/destination/country").asText()).isEqualTo("Таиланд");
        assertThat(body.get("hotels")).isNotEmpty()
                .allSatisfy(h -> assertThat(h.get("city").asText())
                        .isIn("Пхукет", "Паттайя", "Самуи", "Бангкок", "Краби"));
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
        JsonNode body = search("Абу-Даби на 4 ночи, двое взрослых");
        assertThat(body.get("notice").asText()).contains("Абу-Даби").contains("ОАЭ");
        assertThat(body.at("/hotels/0/city").asText()).isEqualTo("Дубай");
    }

    @Test
    void maldivesFoundWithoutFallback() throws Exception {
        JsonNode body = search("На Мальдивы вдвоём на неделю, до 300 тысяч");

        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/hotels/0/city").asText()).isEqualTo("Северный Мале атолл");
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

    // ------------------------------------------------------------------ регионы и новые направления

    @Test
    void crimeaRegionStaysInCrimeaAndPutsKidsBeachFirst() throws Exception {
        JsonNode body = search("Крым в августе, двое взрослых и ребёнок 5 лет");

        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/understood/intent/destination/region").asText()).isEqualTo("Крым");
        assertThat(body.get("hotels")).isNotEmpty().allSatisfy(h ->
                assertThat(h.get("region").asText()).isEqualTo("Крым"));
        assertThat(body.at("/hotels/0/city").asText()).isEqualTo("Евпатория");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Выборг на выходные в июне    | Выборг",
            "Кронштадт, 2 ночи            | Кронштадт",
            "Брест, 3 ночи                | Брест",
            "Гродно на 2 ночи             | Гродно",
            "Самарканд в апреле           | Самарканд",
            "Ереван на неделю             | Ереван",
            "Сидней в январе              | Сидней",
            "Калининград, 3 ночи          | Калининград",
    })
    void historicCitiesAndNewCountries(String query, String city) throws Exception {
        JsonNode body = search(query);
        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/hotels/0/city").asText()).isEqualTo(city);
        assertThat(body.at("/hotels/0/nearby")).isNotEmpty();
    }

    // ------------------------------------------------------------------ пляж, сезон, доступность

    @Test
    void beachPassportAndSeasonExplainTides() throws Exception {
        JsonNode body = search("Бали в июле с ребёнком 4 лет, мелководье, двое взрослых");

        JsonNode nusaDua = null;
        for (JsonNode h : body.get("hotels")) {
            if (h.get("name").asText().equals("Nusa Dua Lagoon Resort")) {
                nusaDua = h;
            }
        }
        assertThat(nusaDua).isNotNull();
        assertThat(nusaDua.at("/beach/info/tides").asText()).isEqualTo("STRONG");
        assertThat(nusaDua.at("/season/month").asInt()).isEqualTo(7);
        assertThat(nusaDua.get("reasons").toString()).contains("отлив");
        assertThat(body.at("/hotels/0/name").asText()).isNotEqualTo("Nusa Dua Lagoon Resort");
    }

    @Test
    void wheelchairUserGetsAccessibleHotelAndBeach() throws Exception {
        JsonNode body = search("Сочи в июле, папа на инвалидной коляске");

        assertThat(body.at("/hotels/0/name").asText()).isEqualTo("Имеретинский Берег");
        assertThat(body.at("/hotels/0/accessibility/accessibleRoom").asBoolean()).isTrue();
        assertThat(body.at("/hotels/0/beach/info/accessible").asBoolean()).isTrue();
    }

    @Test
    void activityOutOfSeasonIsExplained() throws Exception {
        JsonNode body = search("Боракай в июле, кайтсерфинг");
        assertThat(body.get("hotels").toString()).contains("Кайтсёрфинг");
    }

    // ------------------------------------------------------------------ животные, условия, визы, Китай

    @Test
    void petFriendlyHotelsAndPolicies() throws Exception {
        JsonNode body = search("Москва на 3 ночи с собакой, двое взрослых");

        assertThat(body.at("/hotels/0/policies/pets/allowed").toString()).contains("DOG");
        assertThat(body.at("/hotels/0/policies/checkInFrom").asText()).matches("\\d{2}:\\d{2}");
        assertThat(body.at("/hotels/0/policies/features")).isNotEmpty();
        assertThat(body.at("/hotels/0/entry/regime").asText()).isEqualTo("DOMESTIC");
    }

    @Test
    void longStayBeyondVisaFreeLimitIsFlagged() throws Exception {
        JsonNode body = search("Пхукет на 40 ночей, двое взрослых");
        assertThat(body.at("/hotels/0/entry/maxDays").asInt()).isEqualTo(30);
        assertThat(body.at("/hotels/0/reasons").toString()).contains("понадобится виза");
    }

    @Test
    void visaFreeWithoutDestinationPrefersVisaFreeCountries() throws Exception {
        JsonNode body = search("Куда без визы на море в декабре, двое взрослых");
        assertThat(body.get("hotels")).isNotEmpty().allSatisfy(h ->
                assertThat(h.at("/entry/regime").asText()).isNotEqualTo("VISA_REQUIRED"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Чэнду в октябре на 5 ночей   | Чэнду",
            "Сиань, 3 ночи                | Сиань",
            "Чжанцзяцзе, 2 ночи           | Чжанцзяцзе",
            "Ханчжоу на выходные          | Ханчжоу",
            "Харбин в январе              | Харбин",
            "Махачкала, 4 ночи            | Махачкала",
            "Новосибирск, 2 ночи          | Новосибирск",
    })
    void newCities(String query, String city) throws Exception {
        JsonNode body = search(query);
        assertThat(body.get("notice").isNull()).as(body.toString()).isTrue();
        assertThat(body.at("/hotels/0/city").asText()).isEqualTo(city);
    }

    // ------------------------------------------------------------------ дельфины, дороги, шопинг

    @Test
    void dolphinsInCrimeaInSummer() throws Exception {
        JsonNode body = search("Крым в августе, хочу увидеть дельфинов, двое взрослых");
        assertThat(body.at("/hotels/0/wildlife").toString()).contains("DOLPHINS");
        assertThat(body.at("/hotels/0/reasons").toString()).contains("Дельфины");
    }

    @Test
    void shoppingNextToHotel() throws Exception {
        JsonNode body = search("Бангкок на 3 ночи, торговый центр рядом");
        assertThat(body.at("/hotels/0/reasons").toString()).contains("мин пешком");
        assertThat(body.at("/hotels/0/surroundings/roadNoise").asText()).isNotBlank();
    }

    @Test
    void islandWithoutCarsIsExplained() throws Exception {
        JsonNode body = search("Сямынь, 3 ночи, приедем на машине");
        assertThat(body.at("/hotels/0/surroundings/carAccess").asText()).isEqualTo("DOOR");
    }

    // ------------------------------------------------------------------ отзывы и гид

    @Test
    void reviewsCanBeFilteredByTripType() throws Exception {
        String id = search("Турция, всё включено, аквапарк, двое взрослых и ребёнок 7 лет").at("/hotels/0/hotelId").asText();

        MvcResult result = mvc.perform(get("/api/v1/hotels/{id}/reviews", id).param("tripType", "FAMILY").param("sort", "BEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andReturn();
        JsonNode page = json.readTree(result.getResponse().getContentAsByteArray());

        assertThat(page.at("/summary/count").asInt()).isEqualTo(page.get("total").asInt());
        assertThat(page.get("items")).allSatisfy(r -> assertThat(r.get("tripType").asText()).isEqualTo("FAMILY"));
        assertThat(page.at("/summary/byTripType").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void beachPassportAndBeachReviews() throws Exception {
        String beachId = search("Пхукет, Ката, двое").at("/hotels/0/beach/info/id").asText();
        mvc.perform(get("/api/v1/beaches/{id}", beachId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surface").isNotEmpty());
        mvc.perform(get("/api/v1/beaches/{id}/reviews", beachId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void seasonEndpointReturnsHazards() throws Exception {
        mvc.perform(get("/api/v1/guide/season").param("country", "Австралия").param("city", "Кэрнс").param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hazards[0].kind").isNotEmpty());
    }

    @Test
    void reviewsOfUnknownHotelAre404() throws Exception {
        mvc.perform(get("/api/v1/hotels/{id}/reviews", UUID.randomUUID()))
                .andExpect(status().isNotFound());
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
