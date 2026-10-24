package ru.chevteam.onestep.intent;

public enum TravelStyle {
    SOLO, COUPLE, FAMILY, GROUP;

    public static TravelStyle of(Guests guests) {
        if (guests.children() > 0) {
            return FAMILY;
        }
        return switch (guests.adults()) {
            case 1 -> SOLO;
            case 2 -> COUPLE;
            default -> GROUP;
        };
    }
}
