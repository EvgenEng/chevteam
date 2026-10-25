package ru.chevteam.onestep.enrichment.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param runOnStartup пересчитать характеристики при старте (снимок OSM лежит в jar, это 1–2 секунды)
 * @param snapshot     откуда читать снимок OpenStreetMap: шаблон ресурсов
 * @param region       как называть проработанный регион в интерфейсе
 * @param south        граница региона (широта, юг)
 */
@ConfigurationProperties(prefix = "app.enrichment")
public record EnrichmentProperties(Boolean runOnStartup, String snapshot, String region,
                                   Double south, Double west, Double north, Double east) {

    public EnrichmentProperties {
        runOnStartup = runOnStartup == null || runOnStartup;
        snapshot = snapshot == null ? "classpath:osm/yalta-alushta/*.json" : snapshot;
        region = region == null ? "Ялта — Алушта" : region;
        south = south == null ? 44.38 : south;
        west = west == null ? 33.95 : west;
        north = north == null ? 44.72 : north;
        east = east == null ? 34.46 : east;
    }
}
