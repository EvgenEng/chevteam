package ru.chevteam.onestep.it;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Реальные PostGIS, Redis и Kafka в контейнерах — интеграционные тесты проверяют настоящий SQL, а не моки.
 * Таймаут запуска увеличен: на ноутбуке с Docker Desktop (и запущенным рядом docker compose) Kafka стартует дольше минуты.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final Duration STARTUP = Duration.ofMinutes(4);

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4")
                .asCompatibleSubstituteFor("postgres")).withStartupTimeout(STARTUP);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379).withStartupTimeout(STARTUP);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1")).withStartupTimeout(STARTUP);
    }

    /** Фиксированная «сегодняшняя» дата — чтобы «в мае» всегда означало май 2027. */
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneId.of("Europe/Moscow"));
    }
}
