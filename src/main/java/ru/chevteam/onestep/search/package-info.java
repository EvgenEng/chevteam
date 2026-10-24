/**
 * Поиск и объяснимое ранжирование отелей по {@link ru.chevteam.onestep.intent.SearchIntent}.
 * <p>
 * Read-side модуль (CQRS-lite): кандидаты выбираются одним SQL-запросом через JdbcClient
 * с PostGIS-фильтрами, затем оцениваются чистой доменной логикой без фреймворков.
 */
package ru.chevteam.onestep.search;
