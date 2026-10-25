package ru.chevteam.onestep.metrics.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.metrics.GuestEventType;
import ru.chevteam.onestep.metrics.TimeToChoice;
import ru.chevteam.onestep.metrics.internal.GuestMetrics;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Метрики", description = "Время до выбора отеля: от первого запроса гостя до «Забронировать»")
public class MetricsController {

    private final GuestMetrics metrics;

    public MetricsController(GuestMetrics metrics) {
        this.metrics = metrics;
    }

    @PostMapping("/metrics/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Событие гостя: поиск, сравнение, выбор, бронь. Без текста запроса и данных гостя")
    public void event(@Valid @RequestBody EventRequest request) {
        metrics.record(request.sessionId(), request.type(), request.rank(), request.results());
    }

    @GetMapping("/admin/metrics")
    @Operation(summary = "Время до выбора отеля за 30 дней: медиана, число запросов, выбор из топ-3")
    public TimeToChoice summary() {
        return metrics.summary();
    }

    @DeleteMapping("/admin/metrics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Очистить метрики — например, перед демонстрацией")
    public void reset() {
        metrics.reset();
    }

    public record EventRequest(@NotNull UUID sessionId, @NotNull GuestEventType type,
                               @Min(1) @Max(100) Integer rank, @Min(0) @Max(1000) Integer results) {
    }
}
