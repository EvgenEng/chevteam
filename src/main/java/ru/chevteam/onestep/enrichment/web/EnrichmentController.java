package ru.chevteam.onestep.enrichment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.HotelFacts;
import ru.chevteam.onestep.enrichment.internal.EnrichmentPipeline;
import ru.chevteam.onestep.enrichment.internal.PipelineRun;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Пайплайн характеристик: факты отеля, запуск пересчёта, характеристики «текстом» и экстранет отельера.
 * Роли в прототипе переключаются без авторизации — организаторы разрешили это мокать.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Характеристики", description = "Что система сама узнала об отеле по открытым данным и как это сверяется с заявлениями отеля")
public class EnrichmentController {

    private final EnrichmentPipeline pipeline;
    private final HotelFacts facts;

    public EnrichmentController(EnrichmentPipeline pipeline, HotelFacts facts) {
        this.pipeline = pipeline;
        this.facts = facts;
    }

    @GetMapping("/hotels/{id}/facts")
    @Operation(summary = "Факты об отеле с источником, методом, уверенностью и сверкой заявлений отеля")
    public HotelFacts.Profile hotelFacts(@PathVariable UUID id) {
        return facts.profiles(List.of(id)).getOrDefault(id, HotelFacts.Profile.EMPTY);
    }

    // ------------------------------------------------------------------ админка

    @PostMapping("/admin/enrichment/run")
    @Operation(summary = "Пересчитать характеристики всех отелей региона")
    public PipelineRun run() {
        return pipeline.run("ручной запуск");
    }

    @GetMapping("/admin/enrichment")
    @Operation(summary = "Состояние пайплайна: регион, последние запуски, сколько фактов каждого вида")
    public Map<String, Object> status() {
        return Map.of("region", pipeline.region(), "runs", pipeline.runs(), "facts", pipeline.factCounts(),
                "custom", pipeline.customs(), "hotels", pipeline.hotels());
    }

    @PostMapping("/admin/characteristics")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить характеристику текстом: «аптека в 5 минутах», «нет ночных клубов рядом»")
    public EnrichmentPipeline.AddedCharacteristic add(@Valid @RequestBody TextRequest request) {
        return pipeline.addFromText(request.text());
    }

    @DeleteMapping("/admin/characteristics/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить добавленную характеристику")
    public void delete(@PathVariable String code) {
        pipeline.deleteCustom(code);
    }

    // ------------------------------------------------------------------ экстранет отельера

    @GetMapping("/extranet/claim-options")
    @Operation(summary = "Что отельер может заявить о своём отеле")
    public Map<String, Map<String, String>> claimOptions() {
        return pipeline.claimOptions();
    }

    @PutMapping("/extranet/hotels/{id}/claims/{code}")
    @Operation(summary = "Заявление отельера — сохраняется и сразу сверяется с картой")
    public ClaimCheck declare(@PathVariable UUID id, @PathVariable String code, @Valid @RequestBody ClaimRequest request) {
        return pipeline.declare(id, code, request.level(), request.author());
    }

    public record TextRequest(@NotBlank @Size(max = 200) String text) {
    }

    public record ClaimRequest(@NotBlank @Size(max = 20) String level, @Size(max = 120) String author) {
    }
}
