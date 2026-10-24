package ru.chevteam.onestep.intent.llm;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chevteam.onestep.intent.ExtractionResult;
import ru.chevteam.onestep.intent.IntentExtractor;
import ru.chevteam.onestep.intent.IntentSource;
import ru.chevteam.onestep.intent.rules.RuleAnalysis;
import ru.chevteam.onestep.intent.rules.RuleBasedIntentExtractor;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Главная реализация {@link IntentExtractor}: правила + LLM под circuit breaker.
 * <p>
 * Правила выполняются всегда (дёшево и детерминированно). LLM дополняет их, если доступен.
 * Любой сбой LLM деградирует до правил — пользователь получает результат, а не ошибку.
 */
public final class HybridIntentExtractor implements IntentExtractor {

    private static final Logger log = LoggerFactory.getLogger(HybridIntentExtractor.class);
    static final String DEGRADED_NOTE = "AI-помощник сейчас недоступен — запрос разобран по правилам";

    private final RuleBasedIntentExtractor rules;
    private final LlmIntentExtractor llm;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meters;
    private final Clock clock;

    public HybridIntentExtractor(RuleBasedIntentExtractor rules, LlmIntentExtractor llm,
                                 CircuitBreaker circuitBreaker, MeterRegistry meters, Clock clock) {
        this.rules = rules;
        this.llm = llm;
        this.circuitBreaker = circuitBreaker;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    public ExtractionResult extract(String query) {
        RuleAnalysis analysis = rules.analyze(query);
        try {
            LlmIntentPayload payload = circuitBreaker.executeSupplier(() -> llm.extract(query));
            meters.counter("onestep.intent.extraction", "source", "ai", "provider", llm.provider()).increment();
            return IntentMerger.merge(payload, analysis, LocalDate.now(clock));
        } catch (CallNotPermittedException e) {
            log.debug("LLM circuit is open, using rules");
            return degraded(analysis, "circuit_open");
        } catch (RuntimeException e) {
            log.warn("LLM intent extraction failed ({}), falling back to rules: {}", llm.provider(), e.toString());
            return degraded(analysis, "error");
        }
    }

    private ExtractionResult degraded(RuleAnalysis analysis, String reason) {
        meters.counter("onestep.intent.extraction", "source", "rules", "reason", reason).increment();
        ExtractionResult base = new ExtractionResult(analysis.intent(), IntentSource.RULES,
                analysis.assumptions().stream().map(n -> n.text()).toList(),
                analysis.clarifications().stream().map(n -> n.text()).toList());
        return base.withSource(IntentSource.RULES, DEGRADED_NOTE);
    }
}
