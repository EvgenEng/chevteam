package ru.chevteam.onestep.enrichment.internal.tools;

import ru.chevteam.onestep.enrichment.internal.geo.DemGrid;
import ru.chevteam.onestep.enrichment.internal.geo.Geo;
import ru.chevteam.onestep.enrichment.internal.geo.Point;
import ru.chevteam.onestep.enrichment.internal.osm.OsmElement;
import ru.chevteam.onestep.enrichment.internal.osm.OsmSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Характеристики по карте высот SRTM в связке с OpenStreetMap: подъём от моря к отелю и вид на море.
 * Два независимых набора данных дают то, чего нет ни в одном из них по отдельности.
 */
public final class TerrainCharacteristics {

    public static final String CLIMB_TO_SEA = "CLIMB_TO_SEA";
    public static final String SEA_VIEW = "SEA_VIEW";
    public static final String SUNSET_VIEW = "SUNSET_VIEW";

    /** Глаза наблюдателя: 3-й этаж. */
    static final double EYE_M = 9;
    static final double VIEW_RANGE_M = 4000;
    static final double VIEW_STEP_M = 30;
    static final int RAYS = 72;

    private TerrainCharacteristics() {
    }

    public static List<Characteristic> all(DemGrid dem) {
        return List.of(new ClimbToSea(dem), new SeaView(dem, false), new SeaView(dem, true));
    }

    /**
     * Подъём от моря: профиль высот по прямой «берег → отель» с шагом 15 м, сумма подъёмов на обратном пути.
     * Время в гору — правило Нейсмита: +1 минута на каждые 10 м набора высоты.
     */
    static final class ClimbToSea implements Characteristic {
        private final DemGrid dem;

        ClimbToSea(DemGrid dem) {
            this.dem = dem;
        }

        public String code() { return CLIMB_TO_SEA; }
        public String title() { return "Подъём от моря"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            if (!dem.covers(site.location())) {
                return Optional.empty();
            }
            Optional<OsmSnapshot.Nearby> sea = osm.nearest(site.location(), 2500, BuiltIns.SEA);
            if (sea.isEmpty()) {
                return Optional.empty();
            }
            Point shore = closest(site.location(), sea.get().element());
            double distance = Geo.meters(shore, site.location());
            int steps = Math.max(1, (int) Math.ceil(distance / 15));
            double ascent = 0;
            double prev = Math.max(0, dem.elevation(shore));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                Point p = new Point(shore.lat() + (site.location().lat() - shore.lat()) * t,
                        shore.lon() + (site.location().lon() - shore.lon()) * t);
                double h = Math.max(0, dem.elevation(p));
                if (h > prev) {
                    ascent += h - prev;
                }
                prev = h;
            }
            double above = Math.max(0, dem.elevation(site.location()) - Math.max(0, dem.elevation(shore)));
            int flat = Geo.walkMinutes(distance);
            int uphill = flat + (int) Math.round(ascent / 10);
            String level = ascent < 15 ? "FLAT" : ascent < 45 ? "MODERATE" : "STEEP";
            String value = level.equals("FLAT")
                    ? "почти ровно: отель на " + Math.round(above) + " м выше моря"
                    : "отель на " + Math.round(above) + " м выше моря · обратно в гору ≈" + uphill + " мин";
            return Optional.of(new Computed(value, level, (double) Math.round(ascent), 0.7,
                    "профиль высот SRTM (≈30 м) по прямой от берега OSM к отелю, +1 мин на 10 м подъёма",
                    "набор высоты " + Math.round(ascent) + " м на " + Format.meters(distance) + ", вниз к морю ≈" + flat + " мин"));
        }

        private static Point closest(Point from, OsmElement e) {
            Point best = e.geometry().get(0);
            double d = Double.POSITIVE_INFINITY;
            for (Point p : e.geometry()) {
                double m = Geo.meters(from, p);
                if (m < d) {
                    d = m;
                    best = p;
                }
            }
            return best;
        }
    }

    /**
     * Вид на море: 72 луча по кругу с высоты 3-го этажа. Луч «видит море», если доходит до точки на уровне моря
     * и рельеф по дороге не поднимается выше линии взгляда. Сектор обзора = число таких лучей × 5°.
     * Закаты — то же, но только лучи на запад (азимут 225–315°).
     */
    static final class SeaView implements Characteristic {
        private final DemGrid dem;
        private final boolean sunset;

        SeaView(DemGrid dem, boolean sunset) {
            this.dem = dem;
            this.sunset = sunset;
        }

        public String code() { return sunset ? SUNSET_VIEW : SEA_VIEW; }
        public String title() { return sunset ? "Закаты над морем" : "Вид на море"; }

        public Optional<Computed> compute(Site site, OsmSnapshot osm) {
            if (!dem.covers(site.location()) || osm.nearest(site.location(), VIEW_RANGE_M, BuiltIns.SEA).isEmpty()) {
                return Optional.empty();
            }
            double eye = Math.max(0, dem.elevation(site.location())) + EYE_M;
            int visible = 0;
            double sumSin = 0;
            double sumCos = 0;
            for (int i = 0; i < RAYS; i++) {
                double az = i * 360.0 / RAYS;
                if (sunset && (az < 225 || az > 315)) {
                    continue;
                }
                if (seesSea(site.location(), eye, az)) {
                    visible++;
                    sumSin += Math.sin(Math.toRadians(az));
                    sumCos += Math.cos(Math.toRadians(az));
                }
            }
            int sector = visible * 360 / RAYS;
            String method = "линия видимости по рельефу SRTM с высоты 3-го этажа, 72 луча до 4 км; соседние здания не учтены";
            if (sunset) {
                String level = sector >= 15 ? "YES" : "NO";
                return Optional.of(new Computed(level.equals("YES") ? "море на западе видно — закаты над водой" : "закат — за горами или домами",
                        level, (double) sector, 0.5, method, "сектор моря на запад ≈" + sector + "°"));
            }
            String level = sector >= 30 ? "YES" : sector > 0 ? "PARTIAL" : "NO";
            String value = switch (level) {
                case "YES" -> "море видно с 3-го этажа: сектор ≈" + sector + "° на " + direction(Math.toDegrees(Math.atan2(sumSin, sumCos)));
                case "PARTIAL" -> "море видно узкой полосой (≈" + sector + "°)";
                default -> "моря не видно: закрывает рельеф";
            };
            return Optional.of(new Computed(value, level, (double) sector, 0.5, method,
                    "высота отеля ≈" + Math.round(eye - EYE_M) + " м, лучей до моря: " + visible + " из " + RAYS));
        }

        /**
         * Идём по лучу до 4 км: море в точке видно, если линия взгляда к нему выше всего, что встретилось ближе.
         * Ближняя вода может быть скрыта парапетом набережной, а дальняя — видна поверх него, поэтому не останавливаемся
         * на первой точке моря.
         */
        private boolean seesSea(Point from, double eye, double az) {
            double maxSlope = Double.NEGATIVE_INFINITY;
            for (double d = VIEW_STEP_M; d <= VIEW_RANGE_M; d += VIEW_STEP_M) {
                Point p = DemGrid.move(from, d, az);
                double h = dem.elevation(p);
                if (Double.isNaN(h)) {
                    return false;
                }
                double slope = (h - eye) / d;
                if (h <= 0.5 && slope >= maxSlope) {
                    return true;
                }
                maxSlope = Math.max(maxSlope, slope);
            }
            return false;
        }

        static String direction(double azimuth) {
            String[] names = {"север", "северо-восток", "восток", "юго-восток", "юг", "юго-запад", "запад", "северо-запад"};
            int i = (int) Math.round(((azimuth % 360) + 360) % 360 / 45.0) % 8;
            return names[i];
        }
    }
}
