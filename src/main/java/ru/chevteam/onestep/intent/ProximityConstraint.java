package ru.chevteam.onestep.intent;

import java.util.Locale;
import java.util.Objects;

/**
 * «До Эрмитажа пешком минут 15». {@code landmark} — слово из запроса в любой форме:
 * сопоставление с достопримечательностью выполняется по основе слова на стороне каталога.
 */
public record ProximityConstraint(String landmark, int maxWalkMinutes) {

    public ProximityConstraint {
        Objects.requireNonNull(landmark, "landmark");
        landmark = landmark.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        if (landmark.isEmpty()) {
            throw new IllegalArgumentException("landmark must not be blank");
        }
        if (maxWalkMinutes < 1 || maxWalkMinutes > 120) {
            throw new IllegalArgumentException("maxWalkMinutes must be in [1, 120]: " + maxWalkMinutes);
        }
    }
}
