package ru.chevteam.onestep.enrichment.internal;

import org.springframework.stereotype.Component;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.HotelFact;
import ru.chevteam.onestep.enrichment.HotelFacts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class JdbcHotelFacts implements HotelFacts {

    private final FactRepository repo;
    private final EnrichmentPipeline pipeline;

    JdbcHotelFacts(FactRepository repo, EnrichmentPipeline pipeline) {
        this.repo = repo;
        this.pipeline = pipeline;
    }

    @Override
    public List<HotelFact> facts(UUID hotelId) {
        return repo.facts(List.of(hotelId));
    }

    @Override
    public Map<UUID, Profile> profiles(Collection<UUID> hotelIds) {
        List<UUID> ids = new ArrayList<>(hotelIds);
        Map<UUID, List<HotelFact>> facts = new HashMap<>();
        for (HotelFact f : repo.facts(ids)) {
            facts.computeIfAbsent(f.hotelId(), k -> new ArrayList<>()).add(f);
        }
        Map<UUID, List<ClaimCheck>> checks = pipeline.checks(ids);
        Map<UUID, Profile> result = new HashMap<>();
        for (UUID id : ids) {
            List<HotelFact> f = facts.getOrDefault(id, List.of());
            List<ClaimCheck> c = checks.getOrDefault(id, List.of());
            if (!f.isEmpty() || !c.isEmpty()) {
                result.put(id, new Profile(f, c, ClaimVerifier.trust(c), CardScoring.score(c)));
            }
        }
        return result;
    }
}
