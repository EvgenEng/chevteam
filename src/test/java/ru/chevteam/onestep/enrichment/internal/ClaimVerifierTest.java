package ru.chevteam.onestep.enrichment.internal;

import org.junit.jupiter.api.Test;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.FactSource;
import ru.chevteam.onestep.enrichment.HotelFact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimVerifierTest {

    static final UUID HOTEL = UUID.randomUUID();

    static FactRepository.Claim claim(String code, String level) {
        return new FactRepository.Claim(HOTEL, code, level, "заявление", "отельер", Instant.now());
    }

    static Optional<HotelFact> fact(String code, String level, double confidence) {
        return Optional.of(new HotelFact(HOTEL, code, code, "значение", level, null, confidence, FactSource.OSM, "метод",
                "доказательство", Instant.now()));
    }

    @Test
    void hotelSaysQuietButMapShowsBusyStreet() {
        assertThat(ClaimVerifier.check(claim("ROAD_NOISE", "LOW"), fact("ROAD_NOISE", "HIGH", 0.8)).status())
                .isEqualTo(ClaimStatus.DISPUTED);
    }

    @Test
    void oneStepGapCountsOnlyWithConfidentData() {
        assertThat(ClaimVerifier.check(claim("ROAD_NOISE", "LOW"), fact("ROAD_NOISE", "MEDIUM", 0.8)).status())
                .isEqualTo(ClaimStatus.DISPUTED);
        assertThat(ClaimVerifier.check(claim("STAIRS_TO_SEA", "NONE"), fact("STAIRS_TO_SEA", "SOME", 0.5)).status())
                .isEqualTo(ClaimStatus.CONFIRMED);
    }

    @Test
    void modestHotelIsConfirmed() {
        // отель скромничает: «до моря 10 минут», а по карте первая линия — гость не разочаруется
        assertThat(ClaimVerifier.check(claim("SEA_DISTANCE", "NEAR"), fact("SEA_DISTANCE", "FIRST_LINE", 0.9)).status())
                .isEqualTo(ClaimStatus.CONFIRMED);
    }

    @Test
    void noDataIsNotALie() {
        assertThat(ClaimVerifier.check(claim("BEACH_SURFACE", "SAND"), Optional.empty()).status()).isEqualTo(ClaimStatus.UNVERIFIED);
        assertThat(ClaimVerifier.check(claim("BEACH_SURFACE", "SAND"), fact("BEACH_SURFACE", "PEBBLE", 0.6)).status())
                .isEqualTo(ClaimStatus.UNVERIFIED);
        assertThat(ClaimVerifier.check(claim("BEACH_SURFACE", "SAND"), fact("BEACH_SURFACE", "PEBBLE", 0.8)).status())
                .isEqualTo(ClaimStatus.DISPUTED);
    }

    @Test
    void trustIsShareOfConfirmed() {
        List<ClaimCheck> checks = List.of(
                new ClaimCheck("A", "A", "", "", ClaimStatus.CONFIRMED),
                new ClaimCheck("B", "B", "", "", ClaimStatus.DISPUTED),
                new ClaimCheck("C", "C", "", "", ClaimStatus.CONFIRMED),
                new ClaimCheck("D", "D", "", "", ClaimStatus.UNVERIFIED));
        assertThat(ClaimVerifier.trust(checks)).isEqualTo(0.67);
        assertThat(ClaimVerifier.trust(List.of())).isNull();
    }
}
