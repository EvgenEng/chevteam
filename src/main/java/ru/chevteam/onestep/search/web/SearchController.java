package ru.chevteam.onestep.search.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.search.HotelSearch;
import ru.chevteam.onestep.search.SearchResponse;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Поиск", description = "Поиск отелей одним запросом на естественном языке")
public class SearchController {

    private final HotelSearch hotelSearch;

    public SearchController(HotelSearch hotelSearch) {
        this.hotelSearch = hotelSearch;
    }

    @PostMapping
    @Operation(summary = "Найти отели по свободному тексту",
            description = "Пример: «Сочи, 7 дней в сентябре, 4 звезды, первая линия, с завтраком, до 50 тысяч»")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return hotelSearch.search(request.query().strip());
    }
}
