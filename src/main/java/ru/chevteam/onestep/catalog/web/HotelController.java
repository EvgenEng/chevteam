package ru.chevteam.onestep.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.catalog.HotelCatalog;
import ru.chevteam.onestep.catalog.HotelDetails;
import ru.chevteam.onestep.shared.error.NotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hotels")
@Tag(name = "Каталог")
public class HotelController {

    private final HotelCatalog catalog;

    public HotelController(HotelCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Карточка отеля с тарифами")
    public HotelDetails get(@PathVariable UUID id) {
        return catalog.findById(id).orElseThrow(() -> new NotFoundException("Отель " + id + " не найден"));
    }
}
