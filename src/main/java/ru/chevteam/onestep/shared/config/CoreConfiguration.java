package ru.chevteam.onestep.shared.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class CoreConfiguration {

    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    /** Единый источник времени — подменяется в тестах. */
    @Bean
    public Clock clock() {
        return Clock.system(MOSCOW);
    }

    /** Включает {@code @Timed} на методах сервисов. */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("О!дин шаг — API")
                .version("v1")
                .description("Поиск и бронирование отелей одним запросом на естественном языке"));
    }
}
