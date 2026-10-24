package ru.chevteam.onestep.search.internal;

import ru.chevteam.onestep.intent.AccessNeed;
import ru.chevteam.onestep.intent.Amenity;
import ru.chevteam.onestep.intent.BeachActivity;
import ru.chevteam.onestep.intent.BeachPreference;
import ru.chevteam.onestep.intent.DietNeed;
import ru.chevteam.onestep.intent.Guests;
import ru.chevteam.onestep.intent.LocationFeature;
import ru.chevteam.onestep.intent.ProximityConstraint;
import ru.chevteam.onestep.intent.RoomFeature;
import ru.chevteam.onestep.intent.SearchIntent;
import ru.chevteam.onestep.intent.SoftPreference;
import ru.chevteam.onestep.intent.StayPeriod;
import ru.chevteam.onestep.intent.TransitConstraint;
import ru.chevteam.onestep.intent.TransitKind;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Как мы вас поняли» — короткие чипсы для UI: «Турция», «май 2027», «9 ночей», «2 взр. + ребёнок 7 лет».
 */
final class IntentSummary {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", RU);

    private IntentSummary() {
    }

    /**
     * @param resolvedLandmark каноническое имя первой достопримечательности из запроса («Эрмитаж» вместо «эрмитажа»)
     */
    static List<String> of(SearchIntent intent, String resolvedLandmark) {
        List<String> chips = new ArrayList<>();
        if (intent.destination() != null) {
            chips.add(intent.destination().displayName());
        }
        StayPeriod stay = intent.stay();
        if (stay.checkIn() != null) {
            chips.add("с " + stay.checkIn().format(DAY));
        } else if (stay.month() != null) {
            chips.add(stay.month().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RU) + " " + stay.month().getYear());
        }
        if (stay.nights() != null) {
            chips.add(nights(stay.nights()));
        }
        chips.add(guests(intent.guests()));
        if (intent.budget() != null) {
            chips.add("до " + Money.format(intent.budget().amount())
                    + (intent.budget().scope() == ru.chevteam.onestep.intent.Budget.Scope.PER_NIGHT ? " за ночь" : ""));
        }
        if (intent.minStars() != null) {
            chips.add(intent.minStars() + "★+");
        }
        if (intent.mealPlan() != null) {
            chips.add(intent.mealPlan().title());
        }
        intent.amenities().stream().map(Amenity::title).forEach(chips::add);
        intent.locationFeatures().stream().map(LocationFeature::title).forEach(chips::add);
        for (int i = 0; i < intent.proximity().size(); i++) {
            ProximityConstraint p = intent.proximity().get(i);
            String name = i == 0 && resolvedLandmark != null ? resolvedLandmark : p.landmark();
            chips.add("≤" + p.maxWalkMinutes() + " мин пешком: " + name);
        }
        intent.softPreferences().stream().map(SoftPreference::title).forEach(chips::add);
        intent.beach().stream().map(BeachPreference::title).forEach(chips::add);
        intent.activities().stream().map(BeachActivity::title).forEach(chips::add);
        intent.access().stream().map(AccessNeed::title).forEach(chips::add);
        intent.room().stream().map(RoomFeature::title).forEach(chips::add);
        intent.pets().stream().map(p -> "с питомцем: " + p.title().toLowerCase(RU)).forEach(chips::add);
        intent.wildlife().stream().map(w -> "увидеть: " + w.title().toLowerCase(RU)).forEach(chips::add);
        intent.diet().stream().map(DietNeed::title).forEach(chips::add);
        for (TransitConstraint c : intent.transit()) {
            chips.add(c.kind() == TransitKind.METRO ? "≤" + c.maxMinutes() + " мин до метро"
                    : "до " + c.kind().genitive() + " ≤" + MatchScorer.duration(c.maxMinutes()));
        }
        if (intent.visaFreeOnly()) {
            chips.add("без визы");
        }
        if (intent.transport() != null && intent.transport().originCity() != null) {
            chips.add("вылет: " + intent.transport().originCity()
                    + (intent.transport().directPreferred() ? ", лучше прямой" : ""));
        }
        return List.copyOf(chips);
    }

    private static String nights(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        String word = mod10 == 1 && mod100 != 11 ? "ночь"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "ночи" : "ночей";
        return n + " " + word;
    }

    private static String years(int age) {
        if (age == 0) {
            return "до года";
        }
        int mod10 = age % 10;
        int mod100 = age % 100;
        String word = mod10 == 1 && mod100 != 11 ? "год"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "года" : "лет";
        return age + " " + word;
    }

    private static String guests(Guests g) {
        String adults = g.adults() + " взр.";
        if (g.children() == 0) {
            return adults;
        }
        List<String> ages = g.childrenAges().stream().map(IntentSummary::years).toList();
        return adults + " + " + (g.children() == 1 ? "ребёнок " : "дети ") + String.join(", ", ages);
    }
}
