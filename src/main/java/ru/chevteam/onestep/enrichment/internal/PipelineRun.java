package ru.chevteam.onestep.enrichment.internal;

import java.time.Instant;
import java.util.UUID;

/** Итог запуска пайплайна: сколько отелей, фактов и расхождений с заявлениями отелей. */
public record PipelineRun(UUID id, Instant startedAt, Instant finishedAt, String source, int hotels, int facts, int disputes,
                          String message) {

    public long millis() {
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
