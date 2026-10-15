package dev.pocketlod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LodConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = true, singleplayerGeneration = false, generateFeatures = true;
    public boolean hideCaves = true, showStats = false;
    public int lodRadiusChunks = 32, generationRadiusChunks = 24, maxCachedChunks = 8192;
    public int maxQuads = 49152, graphicsQuality = 2;
    public int generationIntervalMillis = 800, generationMinimumFreeHeapMiB = 128, diskCacheMiB = 48;
    public int targetFps = 30, rebuildIntervalTicks = 20, meshBudgetMicros = 900, meshTilesPerFrame = 8;
    public void validate() {
        lodRadiusChunks = clamp(lodRadiusChunks, 8, 96);
        generationRadiusChunks = clamp(generationRadiusChunks, 6, 64);
        graphicsQuality = clamp(graphicsQuality, 0, 3);
        maxCachedChunks = clamp(maxCachedChunks, 256, 32768);
        maxQuads = clamp(maxQuads, 1024, 131072);
        generationIntervalMillis = clamp(generationIntervalMillis, 100, 10000);
        generationMinimumFreeHeapMiB = clamp(generationMinimumFreeHeapMiB, 32, 1024);
        diskCacheMiB = clamp(diskCacheMiB, 1, 256);
        targetFps = clamp(targetFps, 10, 120);
        rebuildIntervalTicks = clamp(rebuildIntervalTicks, 1, 200);
        meshBudgetMicros = clamp(meshBudgetMicros, 100, 4000);
        meshTilesPerFrame = clamp(meshTilesPerFrame, 1, 64);
        ensureCapacityForRadius();
    }
    public void ensureCapacityForRadius() {
        int radius = Math.max(clamp(lodRadiusChunks, 8, 96), clamp(generationRadiusChunks, 6, 64));
        maxCachedChunks = Math.min(32768, Math.max(maxCachedChunks, (int) Math.ceil(Math.PI * radius * radius)));
        maxQuads = Math.min(131072, Math.max(maxQuads, (int) Math.ceil(Math.PI * radius * radius * 8)));
    }
    public void preset(String name) {
        switch (name) {
            case "mobile4gb", "balanced" -> set(32, 24, 8192, 49152, 2, 800);
            case "lowendpc" -> set(48, 32, 16384, 81920, 2, 500);
            case "ultra" -> set(64, 48, 24576, 131072, 3, 350);
            case "tiny" -> set(16, 12, 2048, 16384, 0, 1200);
            default -> throw new IllegalArgumentException("Unknown preset: " + name);
        }
        generationMinimumFreeHeapMiB = 128; diskCacheMiB = 48;
    }
    private void set(int lod, int gen, int cache, int quads, int quality, int interval) {
        lodRadiusChunks = lod; generationRadiusChunks = gen; maxCachedChunks = cache;
        maxQuads = quads; graphicsQuality = quality; generationIntervalMillis = interval;
    }
    public static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(hi, n)); }
    public static LodConfig load(Path path) throws IOException {
        LodConfig c = new LodConfig();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                c = GSON.fromJson(reader, LodConfig.class);
                if (c == null) c = new LodConfig();
            } catch (RuntimeException e) { throw new IOException("Invalid PocketLOD configuration", e); }
        }
        c.validate(); return c;
    }
    public void save(Path path) throws IOException {
        validate(); Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this));
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
