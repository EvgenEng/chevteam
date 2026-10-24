package ru.chevteam.onestep.intent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.llm.GigaChatClient;
import ru.chevteam.onestep.intent.llm.HybridIntentExtractor;
import ru.chevteam.onestep.intent.llm.LlmClient;
import ru.chevteam.onestep.intent.llm.LlmIntentExtractor;
import ru.chevteam.onestep.intent.llm.LlmProperties;
import ru.chevteam.onestep.intent.llm.YandexGptClient;
import ru.chevteam.onestep.intent.rules.RuleBasedIntentExtractor;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Сборка модуля intent. Доменные классы не зависят от Spring — вся «склейка» здесь.
 */
@Configuration(proxyBeanMethods = false)
public class IntentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IntentConfiguration.class);

    @Bean
    RuleBasedIntentExtractor ruleBasedIntentExtractor(Clock clock) {
        return new RuleBasedIntentExtractor(clock);
    }

    @Bean
    @Primary
    IntentExtractor intentExtractor(RuleBasedIntentExtractor rules, LlmProperties props,
                                    RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                    CircuitBreakerRegistry circuitBreakers, MeterRegistry meters, Clock clock) {
        LlmClient client = switch (props.provider()) {
            case NONE -> null;
            case GIGACHAT -> new GigaChatClient(configured(restClientBuilder, props), props.gigachat(), clock);
            case YANDEXGPT -> new YandexGptClient(configured(restClientBuilder, props), props.yandexgpt());
        };
        if (client == null) {
            log.info("LLM provider is disabled — intent extraction uses deterministic rules only");
            return rules;
        }
        log.info("LLM provider: {}", client.provider());
        return new HybridIntentExtractor(rules, new LlmIntentExtractor(client, objectMapper, clock),
                circuitBreakers.circuitBreaker("llm"), meters, clock);
    }

    private static RestClient.Builder configured(RestClient.Builder builder, LlmProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return builder.clone().requestFactory(factory);
    }
}
