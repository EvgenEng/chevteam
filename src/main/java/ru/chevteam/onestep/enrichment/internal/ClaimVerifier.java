package ru.chevteam.onestep.enrichment.internal;

import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.HotelFact;
import ru.chevteam.onestep.enrichment.internal.tools.BuiltIns;
import ru.chevteam.onestep.enrichment.internal.tools.TerrainCharacteristics;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сверка заявлений отельера с независимыми данными.
 * <p>
 * Принцип: доверяем, но проверяем. Заявление «лучше», чем показывают данные, на две ступени и больше —
 * расхождение; на одну ступень — расхождение только при уверенных данных (≥ 0,75). Заявление «хуже» данных
 * (отель скромничает) — подтверждено: гость не разочаруется. Если данных нет — «не проверено», а не «ложь».
 */
final class ClaimVerifier {

    /** Шкалы от лучшего к худшему для гостя. */
    static final Map<String, List<String>> SCALES = Map.ofEntries(
            Map.entry(BuiltIns.SEA_DISTANCE, List.of("FIRST_LINE", "NEAR", "WALK", "FAR")),
            Map.entry(BuiltIns.ROAD_NOISE, List.of("LOW", "MEDIUM", "HIGH")),
            Map.entry(BuiltIns.STAIRS_TO_SEA, List.of("NONE", "SOME", "MANY")),
            Map.entry(BuiltIns.NIGHTLIFE, List.of("NONE", "SOME", "HIGH")),
            Map.entry(BuiltIns.CONSTRUCTION, List.of("NO", "YES")),
            Map.entry(BuiltIns.FOOD_WALK, List.of("MANY", "SOME", "NONE")),
            Map.entry(BuiltIns.GROCERY, List.of("NEAR", "WALK", "FAR")),
            Map.entry(BuiltIns.BUS_STOP, List.of("NEAR", "WALK", "FAR")),
            Map.entry(BuiltIns.GREENERY, List.of("YES", "NO")),
            Map.entry(TerrainCharacteristics.CLIMB_TO_SEA, List.of("FLAT", "MODERATE", "STEEP")),
            Map.entry(TerrainCharacteristics.SEA_VIEW, List.of("YES", "PARTIAL", "NO")));

    /** Что можно заявить в экстранете — и как это звучит для гостя. */
    static final Map<String, Map<String, String>> CLAIMS = Map.of(
            BuiltIns.SEA_DISTANCE, Map.of("FIRST_LINE", "первая линия, до моря 1–2 минуты", "NEAR", "до моря до 10 минут пешком",
                    "WALK", "до моря до 20 минут пешком", "FAR", "до моря нужно ехать"),
            BuiltIns.ROAD_NOISE, Map.of("LOW", "тихо, оживлённых дорог рядом нет", "MEDIUM", "городская улица рядом",
                    "HIGH", "у оживлённой дороги"),
            BuiltIns.STAIRS_TO_SEA, Map.of("NONE", "к морю без лестниц", "SOME", "к морю несколько ступеней",
                    "MANY", "к морю длинные лестницы"),
            BuiltIns.BEACH_SURFACE, Map.of("SAND", "песчаный пляж", "PEBBLE", "галечный пляж", "CONCRETE", "бетонный пляж",
                    "ROCK", "скалистый берег"),
            BuiltIns.NIGHTLIFE, Map.of("NONE", "баров и клубов рядом нет", "SOME", "рядом пара баров", "HIGH", "рядом ночная жизнь"),
            BuiltIns.CONSTRUCTION, Map.of("NO", "строек рядом нет", "YES", "рядом идёт стройка"),
            BuiltIns.GREENERY, Map.of("YES", "рядом парк", "NO", "парков рядом нет"),
            TerrainCharacteristics.CLIMB_TO_SEA, Map.of("FLAT", "к морю по ровной дороге", "MODERATE", "к морю небольшой спуск",
                    "STEEP", "к морю крутой спуск, обратно в гору"),
            TerrainCharacteristics.SEA_VIEW, Map.of("YES", "номера с видом на море", "PARTIAL", "из части номеров видно море",
                    "NO", "вида на море нет"));

    private ClaimVerifier() {
    }

    static ClaimCheck check(FactRepository.Claim claim, Optional<HotelFact> fact) {
        String title = fact.map(HotelFact::title).orElse(claim.code());
        if (fact.isEmpty() || fact.get().level() == null || "UNKNOWN".equals(fact.get().level())) {
            return new ClaimCheck(claim.code(), title, claim.text(), "данных для проверки нет", ClaimStatus.UNVERIFIED);
        }
        HotelFact f = fact.get();
        String observed = f.value() + " — " + f.evidence();
        List<String> scale = SCALES.get(claim.code());
        if (scale == null) {
            // без шкалы (покрытие пляжа, пользовательские да / нет) — точное совпадение; слабым данным не верим на слово
            ClaimStatus status = claim.level().equals(f.level()) ? ClaimStatus.CONFIRMED
                    : f.confidence() >= 0.7 ? ClaimStatus.DISPUTED : ClaimStatus.UNVERIFIED;
            return new ClaimCheck(claim.code(), f.title(), claim.text(), observed, status);
        }
        int claimed = scale.indexOf(claim.level());
        int actual = scale.indexOf(f.level());
        if (claimed < 0 || actual < 0) {
            return new ClaimCheck(claim.code(), f.title(), claim.text(), observed, ClaimStatus.UNVERIFIED);
        }
        int gap = actual - claimed;
        boolean disputed = gap >= 2 || gap == 1 && f.confidence() >= 0.75;
        return new ClaimCheck(claim.code(), f.title(), claim.text(), observed, disputed ? ClaimStatus.DISPUTED : ClaimStatus.CONFIRMED);
    }

    /** Доля подтверждённых среди проверяемых заявлений. */
    static Double trust(List<ClaimCheck> checks) {
        long verifiable = checks.stream().filter(c -> c.status() != ClaimStatus.UNVERIFIED).count();
        if (verifiable == 0) {
            return null;
        }
        long ok = checks.stream().filter(c -> c.status() == ClaimStatus.CONFIRMED).count();
        return Math.round(100.0 * ok / verifiable) / 100.0;
    }
}
