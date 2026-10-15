package dev.pocketlod.generation;

import dev.pocketlod.cache.TileCache;
import dev.pocketlod.core.TerrainTile;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

/** Near-to-far circular plan; keys and de-duplication have no boxed Longs. */
public final class GenerationPlanner {
    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private int ox = Integer.MIN_VALUE, oz, radius, detail;
    public void plan(int x, int z, int radius, int detail, TileCache cache) {
        if (x == ox && z == oz && this.radius == radius && this.detail == detail && !pending.isEmpty()) return;
        ox = x; oz = z; this.radius = radius; this.detail = detail; pending.clear();
        add(x,z,x,z,radius,detail,cache);
        for (int r=1;r<=radius;r++) {
            for (int u=-r;u<=r;u++) {
                add(x+u,z-r,x,z,radius,detail,cache);
                add(x+u,z+r,x,z,radius,detail,cache);
            }
            for (int u=-r+1;u<r;u++) {
                add(x-r,z+u,x,z,radius,detail,cache);
                add(x+r,z+u,x,z,radius,detail,cache);
            }
        }
    }
    private void add(int x,int z,int ox,int oz,int radius,int detail,TileCache cache) {
        int dx=x-ox,dz=z-oz;
        if (dx*dx+dz*dz>radius*radius) return;
        long key=TileCache.key(x,z); TerrainTile tile=cache.get(key);
        if (tile==null || tile.detail<detail) pending.add(key);
    }
    public boolean isEmpty() { return pending.isEmpty(); }
    public long poll() { return pending.removeFirstLong(); }
    public void clear() { pending.clear(); ox = Integer.MIN_VALUE; }
}
