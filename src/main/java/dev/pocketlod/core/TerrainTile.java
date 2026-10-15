package dev.pocketlod.core;

import java.util.Arrays;

/** Immutable chunk-local surface. Coordinates are in two-block sample units. */
public final class TerrainTile {
    public static final int STEP = 2, SAMPLES = 64, ENTRIES = 85;
    public static final int LEGACY = 0, SURFACE = 1, FEATURES = 2;
    public static final short EMPTY = Short.MIN_VALUE;
    private static final ThreadLocal<short[]> SORT = ThreadLocal.withInitial(() -> new short[SAMPLES]);
    private final short[] heights;
    private final int[] colors, sides;
    public final int detail;

    public TerrainTile(short[] height, int[] color, int[] side, int detail) {
        if (height.length < SAMPLES || color.length < SAMPLES || side.length < SAMPLES)
            throw new IllegalArgumentException("64 samples required");
        if (detail < LEGACY || detail > FEATURES) throw new IllegalArgumentException("detail");
        this.detail = detail;
        heights = Arrays.copyOf(height, ENTRIES);
        colors = Arrays.copyOf(color, ENTRIES);
        sides = Arrays.copyOf(side, ENTRIES);
        short[] scratch = SORT.get();
        for (int stride = 2; stride <= 8; stride <<= 1) {
            for (int z = 0; z < 8; z += stride) for (int x = 0; x < 8; x += stride) {
                int count = 0;
                for (int dz = 0; dz < stride; dz++) for (int dx = 0; dx < stride; dx++) {
                    short h = heights[((z + dz) << 3) + x + dx];
                    if (h != EMPTY) scratch[count++] = h;
                }
                int i = index(x, z, stride);
                if (count == 0) { heights[i] = EMPTY; continue; }
                if (count <= 16) {
                    for (int a = 1; a < count; a++) {
                        short v = scratch[a]; int b = a - 1;
                        while (b >= 0 && scratch[b] > v) { scratch[b + 1] = scratch[b]; b--; }
                        scratch[b + 1] = v;
                    }
                } else Arrays.sort(scratch, 0, count);
                short h = scratch[count / 2]; // Upper median is an actual surface, never a floating average.
                heights[i] = h;
                int r = 0, g = 0, b = 0, sr = 0, sg = 0, sb = 0, n = 0;
                for (int dz = 0; dz < stride; dz++) for (int dx = 0; dx < stride; dx++) {
                    int j = ((z + dz) << 3) + x + dx;
                    if (heights[j] == EMPTY || Math.abs(heights[j] - h) > 2) continue;
                    int c = colors[j], s = sides[j];
                    r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
                    sr += s >> 16 & 255; sg += s >> 8 & 255; sb += s & 255; n++;
                }
                colors[i] = ((r / n) << 16) | ((g / n) << 8) | b / n;
                sides[i] = ((sr / n) << 16) | ((sg / n) << 8) | sb / n;
            }
        }
    }
    public static int index(int x, int z, int stride) {
        return switch (stride) {
            case 1 -> (z << 3) + x;
            case 2 -> 64 + ((z >> 1) << 2) + (x >> 1);
            case 4 -> 80 + ((z >> 2) << 1) + (x >> 2);
            case 8 -> 84;
            default -> throw new IllegalArgumentException("stride");
        };
    }
    public short height(int x, int z, int stride) { return heights[index(x, z, stride)]; }
    public int color(int x, int z, int stride) { return colors[index(x, z, stride)]; }
    public int side(int x, int z, int stride) { return sides[index(x, z, stride)]; }
    public short heightAt(int i) { return heights[i]; }
    public int colorAt(int i) { return colors[i]; }
    public int sideAt(int i) { return sides[i]; }
}
