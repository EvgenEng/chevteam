package ru.chevteam.onestep.guide;

import ru.chevteam.onestep.intent.Wildlife;

import java.util.List;

/** Где, как и когда можно встретить морских обитателей. */
public record WildlifeInfo(Wildlife kind, ViewingMode viewing, List<Integer> months, Chance chance, String note) {

    public WildlifeInfo {
        months = months == null ? List.of() : List.copyOf(months);
    }

    public boolean inSeason(int month) {
        return months.isEmpty() || months.contains(month);
    }
}
