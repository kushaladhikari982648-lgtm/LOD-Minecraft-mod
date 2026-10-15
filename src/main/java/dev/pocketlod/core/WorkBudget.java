package dev.pocketlod.core;

public final class WorkBudget {
    private WorkBudget() {}
    public static long generationDelayMillis(long base, int fps, int targetFps, double serverMillis) {
        int factor = fps >= targetFps ? 1 : fps >= Math.max(1, targetFps / 2) ? 2 : 3;
        if (serverMillis > 140) factor = 4;
        return Math.max(50, base) * factor;
    }
    public static long meshBudgetMicros(long base, int fps, boolean urgent) {
        long scaled = fps < 20 ? Math.max(200, base / 2) : base;
        return urgent ? Math.max(1200, scaled * 2) : Math.max(100, scaled);
    }
    public static int meshTilesPerFrame(int base, int fps, boolean urgent) {
        int n = fps < 20 ? Math.max(1, base / 2) : Math.max(1, base);
        return urgent ? Math.max(8, n * 2) : n;
    }
}
