package ru.chevteam.onestep.enrichment;

/** Итог сверки заявления отельера с независимыми данными. */
public enum ClaimStatus {
    /** Данные подтверждают заявление. */
    CONFIRMED,
    /** Данные противоречат заявлению — гость увидит оба, отельер получит запрос. */
    DISPUTED,
    /** Проверить нечем. */
    UNVERIFIED
}
