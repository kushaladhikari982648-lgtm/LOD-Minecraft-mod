package dev.pocketlod.cache;

import dev.pocketlod.core.TerrainTile;
import dev.pocketlod.core.SurfaceMesher;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;

/** Client-thread-owned insertion ordered primitive cache. Reads never mutate iteration order. */
public final class TileCache implements SurfaceMesher.TileSource {
    private final Long2ObjectLinkedOpenHashMap<TerrainTile> tiles = new Long2ObjectLinkedOpenHashMap<>();
    private int limit;
    private long version;
    public record Entry(long key, TerrainTile tile) {}
    public TileCache(int limit) { this.limit = limit; }
    public static long key(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }
    public static int x(long key) { return (int) key; }
    public static int z(long key) { return (int) (key >>> 32); }
    @Override public TerrainTile get(int x, int z) { return tiles.get(key(x,z)); }
    public TerrainTile get(long key) { return tiles.get(key); }
    public void put(long key, TerrainTile tile) {
        TerrainTile old = tiles.get(key);
        if (old != null && old.detail > tile.detail) return;
        if (old != null && old.detail == tile.detail) {
            boolean equal = true;
            for (int i=0;i<64;i++) if (old.heightAt(i)!=tile.heightAt(i) || old.colorAt(i)!=tile.colorAt(i) || old.sideAt(i)!=tile.sideAt(i)) { equal=false; break; }
            if (equal) return;
        }
        tiles.putAndMoveToLast(key, tile); version++; trim();
    }
    public void setLimit(int limit) { this.limit = Math.max(256, Math.min(32768, limit)); trim(); }
    private void trim() { while (tiles.size() > limit) { tiles.removeFirst(); version++; } }
    public void clear() { tiles.clear(); version++; }
    public int size() { return tiles.size(); }
    public long version() { return version; }
    public List<Entry> snapshot() {
        List<Entry> out = new ArrayList<>(tiles.size());
        for (var e : tiles.long2ObjectEntrySet()) out.add(new Entry(e.getLongKey(), e.getValue()));
        return out;
    }
}
