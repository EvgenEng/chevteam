package ru.chevteam.onestep.catalog;

import java.util.Optional;
import java.util.UUID;

/** Публичный API каталога. */
public interface HotelCatalog {

    Optional<HotelDetails> findById(UUID id);
}
