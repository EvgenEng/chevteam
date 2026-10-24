package ru.chevteam.onestep.search;

import ru.chevteam.onestep.guide.BeachInfo;

/** Ближайший к отелю пляж и сколько до него идти. */
public record BeachView(BeachInfo info, int distanceMeters, int walkMinutes) {
}
