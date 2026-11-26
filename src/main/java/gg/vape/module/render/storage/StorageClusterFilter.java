package gg.vape.module.render.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Filters enabled, in-range storage by the number of nearby block entities. */
public final class StorageClusterFilter {
    public interface Position {
        int x();
        int y();
        int z();
    }

    private StorageClusterFilter() {}

    public static <T extends Position> List<T> filter(List<T> targets, int radius, int minimum) {
        if (minimum <= 1 || targets.isEmpty()) return targets;
        if (minimum > targets.size()) return new ArrayList<T>();
        int cellSize = Math.max(1, radius);
        long radiusSquared = (long)cellSize * cellSize;
        Map<Cell, List<T>> cells = new HashMap<Cell, List<T>>();
        for (T target : targets) {
            Cell key = new Cell(Math.floorDiv(target.x(), cellSize), Math.floorDiv(target.y(), cellSize),
                    Math.floorDiv(target.z(), cellSize));
            List<T> cell = cells.get(key);
            if (cell == null) {
                cell = new ArrayList<T>();
                cells.put(key, cell);
            }
            cell.add(target);
        }
        List<T> result = new ArrayList<T>();
        for (T target : targets) {
            int cx = Math.floorDiv(target.x(), cellSize), cy = Math.floorDiv(target.y(), cellSize);
            int cz = Math.floorDiv(target.z(), cellSize), count = 0;
            boolean qualifies = false;
            for (int dx = -1; dx <= 1 && !qualifies; ++dx) {
                for (int dy = -1; dy <= 1 && !qualifies; ++dy) {
                    for (int dz = -1; dz <= 1 && !qualifies; ++dz) {
                        List<T> cell = cells.get(new Cell(cx + dx, cy + dy, cz + dz));
                        if (cell == null) continue;
                        for (T other : cell) {
                            long x = (long)other.x() - target.x();
                            long y = (long)other.y() - target.y();
                            long z = (long)other.z() - target.z();
                            if ((double)x * x + (double)y * y + (double)z * z <= radiusSquared && ++count >= minimum) {
                                qualifies = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (qualifies) result.add(target);
        }
        return result;
    }

    private static final class Cell {
        private final int x, y, z;
        private Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int hashCode() { return (this.x * 31 + this.y) * 31 + this.z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell cell = (Cell)other;
            return this.x == cell.x && this.y == cell.y && this.z == cell.z;
        }
    }
}
