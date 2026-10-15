package dev.pocketlod.generation;

/** FPS and server tick time affect pacing, never eligibility. */
public final class GenerationPolicy {
    private GenerationPolicy() {}
    public static boolean allowed(boolean enabled, boolean integrated, boolean stationary,
                                  long freeHeapMiB, int minimumFreeHeapMiB) {
        return enabled && integrated && stationary && freeHeapMiB >= minimumFreeHeapMiB;
    }
}
