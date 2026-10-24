package ru.chevteam.onestep.guide;

import java.util.List;

/**
 * Погода и риски в месяц поездки.
 *
 * @param waterC температура моря; {@code null} — у места нет купального водоёма
 * @param waves  {@code null} — не морское направление
 */
public record SeasonInfo(
        int month,
        int airC,
        Integer waterC,
        RainLevel rain,
        WaveLevel waves,
        Comfort comfort,
        String note,
        List<SeasonalHazard> hazards) {

    public SeasonInfo {
        hazards = hazards == null ? List.of() : List.copyOf(hazards);
    }

    public record SeasonalHazard(HazardKind kind, Severity severity, String note) {
    }
}
