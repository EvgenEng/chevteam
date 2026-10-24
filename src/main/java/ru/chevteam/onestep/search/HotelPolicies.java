package ru.chevteam.onestep.search;

import ru.chevteam.onestep.intent.PetKind;
import ru.chevteam.onestep.intent.RoomFeature;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Условия проживания: время заезда и выезда, животные, удобства «есть / нет».
 *
 * @param checkInFrom   «14:00»
 * @param checkOutUntil «12:00»
 */
public record HotelPolicies(String checkInFrom, String checkOutUntil, String stayNote, Pets pets, List<Feature> features) {

    public HotelPolicies {
        features = features == null ? List.of() : List.copyOf(features);
    }

    public Optional<Feature> feature(RoomFeature f) {
        return features.stream().filter(x -> x.feature() == f).findFirst();
    }

    /**
     * @param fee за ночь в рублях; 0 — бесплатно; {@code null} — животных не принимают
     */
    public record Pets(Set<PetKind> allowed, BigDecimal fee, String note) {

        public Pets {
            allowed = allowed == null ? Set.of() : Set.copyOf(allowed);
        }

        public boolean any() {
            return !allowed.isEmpty();
        }
    }

    public record Feature(RoomFeature feature, boolean available, String note) {
    }
}
