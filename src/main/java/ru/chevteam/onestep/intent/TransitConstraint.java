package ru.chevteam.onestep.intent;

import java.util.Objects;

/** «До аэропорта не больше 40 минут», «рядом с метро». */
public record TransitConstraint(TransitKind kind, int maxMinutes) {

    public TransitConstraint {
        Objects.requireNonNull(kind, "kind");
        if (maxMinutes < 1 || maxMinutes > 600) {
            throw new IllegalArgumentException("maxMinutes must be in [1, 600]: " + maxMinutes);
        }
    }
}
