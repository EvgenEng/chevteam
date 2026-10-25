package ru.chevteam.onestep.enrichment;

/**
 * Откуда агент данных берёт недостающие объекты OSM. По умолчанию — открытый Overpass API;
 * другой источник (свой сервер Overpass, корпоративное зеркало, заготовка в тестах) подключается своим бином.
 */
public interface OsmSourceFetcher {

    /** Выполняет запрос Overpass QL и возвращает ответ в формате Overpass JSON. */
    Fetched fetch(String query);

    record Fetched(String json, String server) {
    }
}
