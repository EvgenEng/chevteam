package ru.chevteam.onestep.enrichment;

/**
 * Характеристики отелей пересчитаны (запуск пайплайна, новая характеристика, заявление отельера):
 * закэшированная выдача устарела.
 */
public record HotelFactsUpdated(int hotels, String reason) {
}
