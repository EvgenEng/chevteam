package ru.chevteam.onestep.search;

/**
 * Публичный API модуля поиска: «один запрос — готовая подборка».
 */
public interface HotelSearch {

    SearchResponse search(String query);
}
