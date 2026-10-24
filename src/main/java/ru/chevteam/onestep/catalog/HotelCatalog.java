package ru.chevteam.onestep.catalog;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Публичный API каталога. */
public interface HotelCatalog {

    Optional<HotelDetails> findById(UUID id);

    /**
     * Цена тарифа за проживание: каждая ночь считается по коэффициенту своего месяца
     * (поездка 28 мая – 3 июня захватывает два сезона).
     */
    Optional<RateQuote> quote(UUID rateId, LocalDate checkIn, int nights);

    /** Цена питания, которое отель добавляет к любому своему тарифу. */
    Optional<MealAddonPrice> mealAddon(UUID hotelId, String mealPlan);
}
