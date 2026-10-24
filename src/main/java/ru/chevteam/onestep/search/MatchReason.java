package ru.chevteam.onestep.search;

/**
 * Объяснение, почему отель подходит (или нет). Именно это пользователь видит на карточке.
 */
public record MatchReason(Kind kind, String text) {

    public enum Kind {
        /** ✅ требование выполнено */
        MATCH,
        /** ⚠️ выполнено частично */
        PARTIAL,
        /** ❌ не выполнено */
        MISS,
        /** ℹ️ полезная информация */
        INFO
    }

    public static MatchReason match(String text) {
        return new MatchReason(Kind.MATCH, text);
    }

    public static MatchReason partial(String text) {
        return new MatchReason(Kind.PARTIAL, text);
    }

    public static MatchReason miss(String text) {
        return new MatchReason(Kind.MISS, text);
    }

    public static MatchReason info(String text) {
        return new MatchReason(Kind.INFO, text);
    }
}
