package ru.chevteam.onestep.enrichment.internal.geo;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Карта высот (SRTM, шаг 1″ ≈ 30 м) для региона: высота точки — билинейная интерполяция соседних узлов.
 * Над морем значение 0. Это модель поверхности: здания и деревья в ней сглажены, поэтому выводы о видах —
 * оценка рельефа, а не гарантия конкретного номера.
 */
public final class DemGrid {

    private final double south;
    private final double west;
    private final double north;
    private final double east;
    private final int rows;
    private final int cols;
    private final short[] heights;

    public DemGrid(double south, double west, double north, double east, int rows, int cols, short[] heights) {
        if (heights.length != rows * cols) {
            throw new IllegalArgumentException("heights size mismatch");
        }
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;
        this.rows = rows;
        this.cols = cols;
        this.heights = heights;
    }

    /** Формат {@code tools/dem/crop.py}: "DEM1", границы, размеры, int16 BE с севера на юг. */
    public static DemGrid read(InputStream gz) throws IOException {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(gz)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"DEM1".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IOException("Not a DEM1 file");
            }
            double s = in.readDouble();
            double w = in.readDouble();
            double n = in.readDouble();
            double e = in.readDouble();
            int r = in.readInt();
            int c = in.readInt();
            short[] h = new short[r * c];
            for (int i = 0; i < h.length; i++) {
                h[i] = in.readShort();
            }
            return new DemGrid(s, w, n, e, r, c, h);
        }
    }

    public boolean covers(Point p) {
        return p.lat() >= south && p.lat() <= north && p.lon() >= west && p.lon() <= east;
    }

    /** Высота над уровнем моря, м; вне региона — {@code NaN}. */
    public double elevation(Point p) {
        if (!covers(p)) {
            return Double.NaN;
        }
        double y = (north - p.lat()) / (north - south) * (rows - 1);
        double x = (p.lon() - west) / (east - west) * (cols - 1);
        int y0 = (int) Math.floor(y);
        int x0 = (int) Math.floor(x);
        int y1 = Math.min(rows - 1, y0 + 1);
        int x1 = Math.min(cols - 1, x0 + 1);
        double fy = y - y0;
        double fx = x - x0;
        double top = at(y0, x0) * (1 - fx) + at(y0, x1) * fx;
        double bottom = at(y1, x0) * (1 - fx) + at(y1, x1) * fx;
        return top * (1 - fy) + bottom * fy;
    }

    private double at(int r, int c) {
        return heights[r * cols + c];
    }

    /** Точка на расстоянии и азимуте (0 — север, 90 — восток). */
    public static Point move(Point from, double meters, double azimuthDeg) {
        double a = Math.toRadians(azimuthDeg);
        double dLat = meters * Math.cos(a) / 111_195.0;
        double dLon = meters * Math.sin(a) / (111_195.0 * Math.cos(Math.toRadians(from.lat())));
        return new Point(from.lat() + dLat, from.lon() + dLon);
    }
}
