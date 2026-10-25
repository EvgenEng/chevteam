package ru.chevteam.onestep.enrichment.internal.geo;

import java.util.List;

/**
 * Геометрия на малых расстояниях (до десятков километров): локальная равнопромежуточная проекция
 * вокруг точки отеля. Погрешность на таких расстояниях — доли процента, для «сколько идти до моря» этого хватает.
 */
public final class Geo {

    private static final double EARTH_M = 6_371_000;

    private Geo() {
    }

    public static double meters(Point a, Point b) {
        double la1 = Math.toRadians(a.lat());
        double la2 = Math.toRadians(b.lat());
        double dLat = la2 - la1;
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Расстояние от точки до ломаной (дорога, береговая линия, контур). */
    public static double metersToLine(Point p, List<Point> line) {
        if (line.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (line.size() == 1) {
            return meters(p, line.get(0));
        }
        double best = Double.POSITIVE_INFINITY;
        double kx = Math.cos(Math.toRadians(p.lat())) * Math.toRadians(1) * EARTH_M;
        double ky = Math.toRadians(1) * EARTH_M;
        for (int i = 1; i < line.size(); i++) {
            Point a = line.get(i - 1);
            Point b = line.get(i);
            double ax = (a.lon() - p.lon()) * kx;
            double ay = (a.lat() - p.lat()) * ky;
            double bx = (b.lon() - p.lon()) * kx;
            double by = (b.lat() - p.lat()) * ky;
            double dx = bx - ax;
            double dy = by - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double cx = ax + t * dx;
            double cy = ay + t * dy;
            best = Math.min(best, Math.sqrt(cx * cx + cy * cy));
        }
        return best;
    }

    /** Точка внутри замкнутого контура (луч вправо). */
    public static boolean inside(Point p, List<Point> ring) {
        boolean in = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            Point a = ring.get(i);
            Point b = ring.get(j);
            if ((a.lat() > p.lat()) != (b.lat() > p.lat())
                    && p.lon() < (b.lon() - a.lon()) * (p.lat() - a.lat()) / (b.lat() - a.lat()) + a.lon()) {
                in = !in;
            }
        }
        return in;
    }

    /** Длина ломаной. */
    public static double length(List<Point> line) {
        double sum = 0;
        for (int i = 1; i < line.size(); i++) {
            sum += meters(line.get(i - 1), line.get(i));
        }
        return sum;
    }

    /** Пешком по городу: улицы петляют (×1,25), шаг ~75 м/мин. */
    public static int walkMinutes(double straightMeters) {
        return (int) Math.max(1, Math.ceil(straightMeters * 1.25 / 75));
    }
}
