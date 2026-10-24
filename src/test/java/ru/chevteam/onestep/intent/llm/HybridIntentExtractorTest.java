package ru.chevteam.onestep.intent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.rules.RuleBasedIntentExtractor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HybridIntentExtractorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneId.of("Europe/Moscow"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void usesLlmWhenHealthy() {
        HybridIntentExtractor extractor = hybrid(new StubLlm(
                "```json\n{\"country\":\"Турция\",\"city\":\"Мармарис\",\"minStars\":5}\n```"), CircuitBreaker.ofDefaults("t"));

        ExtractionResult result = extractor.extract("Мармарис, пятёрочка");

        assertThat(result.source()).isEqualTo(IntentSource.AI);
        assertThat(result.intent().destination().city()).isEqualTo("Мармарис");
        assertThat(meters.counter("onestep.intent.extraction", "source", "ai", "provider", "stub").count()).isEqualTo(1);
    }

    @Test
    void degradesToRulesOnFailure() {
        HybridIntentExtractor extractor = hybrid(new StubLlm(null), CircuitBreaker.ofDefaults("t"));

        ExtractionResult result = extractor.extract("Сочи, 4 звезды");

        assertThat(result.source()).isEqualTo(IntentSource.RULES);
        assertThat(result.intent().minStars()).isEqualTo(4);
        assertThat(result.assumptions()).first().isEqualTo(HybridIntentExtractor.DEGRADED_NOTE);
    }

    @Test
    void openCircuitSkipsLlmCalls() {
        CircuitBreaker breaker = CircuitBreaker.of("t", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        StubLlm failing = new StubLlm(null);
        HybridIntentExtractor extractor = hybrid(failing, breaker);

        for (int i = 0; i < 5; i++) {
            assertThat(extractor.extract("Сочи").source()).isEqualTo(IntentSource.RULES);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(failing.calls.get()).isEqualTo(2);
    }

    private HybridIntentExtractor hybrid(LlmClient client, CircuitBreaker breaker) {
        return new HybridIntentExtractor(new RuleBasedIntentExtractor(clock),
                new LlmIntentExtractor(client, new ObjectMapper(), clock), breaker, meters, clock);
    }

    /** Ответ null — имитация падения провайдера. */
    private static final class StubLlm implements LlmClient {
        private final String answer;
        private final AtomicInteger calls = new AtomicInteger();

        StubLlm(String answer) {
            this.answer = answer;
        }

        @Override
        public String provider() {
            return "stub";
        }

        @Override
        public String complete(String systemPrompt, String userMessage) {
            calls.incrementAndGet();
            if (answer == null) {
                throw new LlmException("provider is down");
            }
            return answer;
        }
    }
}
