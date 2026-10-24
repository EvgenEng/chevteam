package ru.chevteam.onestep.guide;

import ru.chevteam.onestep.intent.BeachActivity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Паспорт пляжа: всё, что человек хочет знать до поездки и обычно узнаёт уже на месте.
 *
 * @param lat              центральная точка пляжа
 * @param depthDrop        резкий обрыв глубины (риф, свал, понтон)
 * @param activitySeasons  лучшие месяцы для активностей; нет записи — сезон не ограничен
 * @param seniorsFriendly  удобно пожилым: ровный вход, без длинных лестниц
 * @param accessible       можно добраться до воды на коляске (пандусы, настилы, пляжные коляски)
 */
public record BeachInfo(
        UUID id,
        String name,
        double lat,
        double lon,
        BeachSurface surface,
        boolean artificialSand,
        BeachEntry entry,
        boolean depthDrop,
        String depthNote,
        TideLevel tides,
        String tideNote,
        int lengthM,
        CrowdLevel crowd,
        Set<BeachActivity> activities,
        Map<BeachActivity, List<Integer>> activitySeasons,
        List<String> entertainment,
        boolean kidsFriendly,
        boolean seniorsFriendly,
        boolean accessible,
        String accessibilityNote,
        String description) {

    public BeachInfo {
        activities = activities == null ? Set.of() : Set.copyOf(activities);
        activitySeasons = activitySeasons == null ? Map.of() : Map.copyOf(activitySeasons);
        entertainment = entertainment == null ? List.of() : List.copyOf(entertainment);
    }

    /** Подходит ли место для активности в этом месяце. {@code null} — активность здесь невозможна. */
    public Boolean inSeason(BeachActivity activity, int month) {
        if (!activities.contains(activity)) {
            return null;
        }
        List<Integer> months = activitySeasons.get(activity);
        return months == null || months.isEmpty() || months.contains(month);
    }

    /** Безопасно ли для малышей и неуверенных пловцов: пологий вход и нет обрыва. */
    public boolean gentleForSwimmers() {
        return entry == BeachEntry.GENTLE && !depthDrop;
    }
}
