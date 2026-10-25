package ru.chevteam.onestep.enrichment;

/**
 * Сверка одного заявления отельера.
 *
 * @param claimed  что заявил отель: «тихий отель вдали от дорог»
 * @param observed что показывают данные: «трасса Ялта — Севастополь в 40 м, 7 из 30 отзывов жалуются на шум»
 */
public record ClaimCheck(String code, String title, String claimed, String observed, ClaimStatus status) {
}
