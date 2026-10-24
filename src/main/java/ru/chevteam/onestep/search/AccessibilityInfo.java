package ru.chevteam.onestep.search;

/**
 * Доступная среда отеля.
 *
 * @param stepFree       вход и общие зоны без ступеней (пандусы)
 * @param accessibleRoom номер для гостей на коляске: широкие двери, поручни, душ без поддона
 */
public record AccessibilityInfo(boolean stepFree, boolean accessibleRoom, boolean elevator, Terrain terrain, String note) {
}
