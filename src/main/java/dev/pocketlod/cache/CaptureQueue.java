package dev.pocketlod.cache;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public final class CaptureQueue {
    private final LongLinkedOpenHashSet keys = new LongLinkedOpenHashSet();
    public void add(long key) { if (keys.size() < 32768) keys.add(key); }
    public boolean isEmpty() { return keys.isEmpty(); }
    public long poll() { return keys.removeFirstLong(); }
    public void clear() { keys.clear(); }
}
