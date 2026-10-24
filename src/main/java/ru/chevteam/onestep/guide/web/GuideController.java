package ru.chevteam.onestep.guide.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.guide.BeachInfo;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.TravelGuide;
import ru.chevteam.onestep.shared.error.NotFoundException;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Гид", description = "Пляжи, климат и сезонные риски")
public class GuideController {

    private final TravelGuide guide;

    public GuideController(TravelGuide guide) {
        this.guide = guide;
    }

    @GetMapping("/beaches/{id}")
    @Operation(summary = "Паспорт пляжа: покрытие, вход, глубина, приливы, активности, доступность")
    public BeachInfo beach(@PathVariable UUID id) {
        return guide.beach(id).orElseThrow(() -> new NotFoundException("Пляж " + id + " не найден"));
    }

    @GetMapping("/guide/season")
    @Operation(summary = "Погода и сезонные риски в городе в выбранный месяц")
    public SeasonInfo season(@RequestParam String country, @RequestParam String city,
                             @RequestParam @Min(1) @Max(12) int month) {
        return guide.season(country, city, month)
                .orElseThrow(() -> new NotFoundException("Нет климатических данных для " + city));
    }
}
