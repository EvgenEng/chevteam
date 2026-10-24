package ru.chevteam.onestep.search;

/**
 * Окружение отеля: шум дорог и подъезд.
 *
 * @param roadNoise LOW, MEDIUM, HIGH
 * @param carAccess DOOR — машина к входу; NEARBY — высадка рядом; PEDESTRIAN — пешеходная зона; BOAT — только по воде
 */
public record Surroundings(Level roadNoise, String roadNote, CarAccess carAccess, String accessNote) {

    public enum Level { LOW, MEDIUM, HIGH }

    public enum CarAccess { DOOR, NEARBY, PEDESTRIAN, BOAT }
}
