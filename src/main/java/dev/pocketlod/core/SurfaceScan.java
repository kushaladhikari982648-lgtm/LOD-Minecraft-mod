package dev.pocketlod.core;

public final class SurfaceScan {
    public interface Column {
        boolean emptySection(int y);
        boolean surface(int y);
    }
    private SurfaceScan() {}
    /** Starting height is inclusive; absent vertical sections cost one iteration. */
    public static int findTop(int start, int bottom, Column column) {
        int y = start;
        while (y >= bottom) {
            if (column.emptySection(y)) { y = (y & ~15) - 1; continue; }
            if (column.surface(y)) return y;
            y--;
        }
        return TerrainTile.EMPTY;
    }
}
