package ru.chevteam.onestep.trip.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.trip.TransportMode;
import ru.chevteam.onestep.trip.TripOption;
import ru.chevteam.onestep.trip.TripPlan;
import ru.chevteam.onestep.trip.TripPlanner;
import ru.chevteam.onestep.trip.TripQuote;
import ru.chevteam.onestep.trip.TripRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@Tag(name = "О!План", description = "Поездка целиком: отель, дорога и трансферы в одном бюджете")
public class TripController {

    private final TripPlanner planner;

    public TripController(TripPlanner planner) {
        this.planner = planner;
    }

    public record PlanBody(
            @Size(max = 2000, message = "Описание поездки не длиннее 2000 символов") String text,
            @Size(max = 100) String origin,
            @Size(max = 100) String reserve,
            TransportMode transport,
            @Size(max = 20) List<String> excludeHotels) {
    }

    public record QuoteBody(
            @NotNull UUID hotelId,
            @Size(max = 100) String origin,
            TransportMode transport,
            @NotNull LocalDate checkIn,
            @Min(1) @Max(30) int nights,
            @Min(1) @Max(10) int adults,
            @Size(max = 6) List<@NotNull @Min(0) @Max(17) Integer> childrenAges,
            @Min(0) BigDecimal budget,
            @Min(0) BigDecimal reserve) {
    }

    @PostMapping("/plan")
    @Operation(summary = "Следующий шаг разговора: уточняющий вопрос или варианты поездки",
            description = "Пример: «Ялта, 12–17 ноября, трое взрослых, до 150 тысяч на дорогу, отель и трансферы, нужен бассейн»")
    public TripPlan plan(@Valid @RequestBody PlanBody body) {
        return planner.plan(new TripRequest(body.text(), body.origin(), body.reserve(), body.transport(),
                body.excludeHotels()));
    }

    @PostMapping("/quote")
    @Operation(summary = "Пересчитать поездку после изменения дат, гостей, транспорта или бюджета")
    public TripOption quote(@Valid @RequestBody QuoteBody body) {
        return planner.quote(new TripQuote(body.hotelId(), body.origin(), body.transport(), body.checkIn(),
                body.nights(), body.adults(), body.childrenAges(), body.budget(), body.reserve()));
    }
}
