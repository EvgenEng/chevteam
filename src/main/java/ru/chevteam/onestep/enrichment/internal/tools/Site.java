package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.geo.Point;

import java.util.UUID;

/** Отель как точка на карте — всё, что нужно инструментам. */
public record Site(UUID hotelId, String name, Point location) {
}
