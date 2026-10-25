package ru.chevteam.onestep.enrichment.internal;

import ru.chevteam.onestep.enrichment.OsmSourceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Открытый Overpass API OpenStreetMap (данные под ODbL). Публичные серверы перегружены, поэтому пробуем их по очереди.
 * Адреса серверов — только из настроек, не из запроса пользователя.
 */
@Component
class OverpassFetcher implements OsmSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(OverpassFetcher.class);
    private static final String USER_AGENT = "chevteam-onestep/1.0 (hackathon prototype; github.com/EvgenEng)";

    private final EnrichmentProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    OverpassFetcher(EnrichmentProperties props) {
        this.props = props;
    }

    @Override
    public Fetched fetch(String query) {
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String last = "серверы не настроены";
        for (String url : props.overpass()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(100))
                        .header("User-Agent", USER_AGENT).header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200 && response.body().contains("\"elements\"")) {
                    return new Fetched(response.body(), URI.create(url).getHost());
                }
                last = URI.create(url).getHost() + " ответил " + response.statusCode();
            } catch (IOException e) {
                last = URI.create(url).getHost() + ": " + e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Скачивание прервано", e);
            }
            log.warn("Overpass {} failed: {}", url, last);
        }
        throw new IllegalStateException("Не удалось скачать данные OpenStreetMap: " + last + ". Попробуйте ещё раз чуть позже");
    }
}
