package dev.pocketlod.generation;

/** Epoch invalidation prevents late futures from publishing into a new world/session. */
public final class SingleFlightChannel {
    private long epoch;
    private boolean busy;
    public synchronized long begin() {
        if (busy) return -1;
        busy = true;
        return ++epoch;
    }
    public synchronized boolean accepts(long token) { return busy && epoch == token; }
    public synchronized boolean finish(long token) {
        if (!accepts(token)) return false;
        busy = false; return true;
    }
    public synchronized void cancel() { busy = false; epoch++; }
    public synchronized boolean busy() { return busy; }
}
