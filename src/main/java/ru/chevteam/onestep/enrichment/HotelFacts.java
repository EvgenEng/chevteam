package ru.chevteam.onestep.enrichment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Публичный API пайплайна характеристик. */
public interface HotelFacts {

    List<HotelFact> facts(UUID hotelId);

    /** Для выдачи: факты и сверки сразу для всех отелей страницы — одним запросом. */
    Map<UUID, Profile> profiles(Collection<UUID> hotelIds);

    /**
     * @param trust доля подтверждённых заявлений отеля 0..1 или {@code null}, если сверять было нечего
     * @param card  заполненность и уровень карточки — «Проверенная карточка» видна гостю
     */
    record Profile(List<HotelFact> facts, List<ClaimCheck> checks, Double trust, CardScore card) {

        public static final Profile EMPTY = new Profile(List.of(), List.of(), null, null);

        public Profile {
            facts = facts == null ? List.of() : List.copyOf(facts);
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }
}
