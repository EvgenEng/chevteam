package ru.chevteam.onestep.guide;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Публичный API модуля: справочник пляжей, климата и сезонных рисков. */
public interface TravelGuide {

    Optional<BeachInfo> beach(UUID beachId);

    /**
     * @param month 1..12
     */
    Optional<SeasonInfo> season(String country, String city, int month);

    /** Все пляжи города — чтобы предложить соседний, если ближайший не подходит (кайт, серфинг). */
    List<BeachInfo> beachesIn(String city);

    /** Правила въезда для граждан РФ: сначала исключение для города (Синай), затем правило страны. */
    Optional<EntryInfo> entry(String country, String city);

    /** Кого из морских обитателей можно встретить в этом месте. */
    List<WildlifeInfo> wildlife(String country, String city);
}
