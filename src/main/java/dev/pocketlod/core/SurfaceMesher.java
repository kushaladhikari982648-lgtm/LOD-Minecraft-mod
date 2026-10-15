package dev.pocketlod.core;

import java.util.Arrays;

/** CPU-only, resumable voxel mesher. No Minecraft or GL calls. */
public final class SurfaceMesher {
    @FunctionalInterface public interface TileSource { TerrainTile get(int chunkX, int chunkZ); }
    public static int stride(int distanceSquared, int fineRadius, int quality) {
        int fine = fineRadius + switch (quality) { case 0 -> 0; case 1 -> 4; case 2 -> 10; default -> 18; };
        int medium = fineRadius + switch (quality) { case 0 -> 4; case 1 -> 12; case 2 -> 24; default -> 40; };
        return distanceSquared <= fine * fine ? 1 : distanceSquared <= medium * medium ? 2 : 4;
    }
    public static final class Built {
        public final float[] positions;
        public final int[] colors;
        public final int quads;
        private Built(float[] positions, int[] colors, int quads) {
            this.positions = positions; this.colors = colors; this.quads = quads;
        }
    }
    public static final class Job {
        public final int originChunkX, originChunkZ, preloadInner, quality, radius;
        private final int fineRadius, quadLimit;
        private final TileSource source;
        private final long[] offsets;
        private final boolean[] used = new boolean[64];
        private final float[] scratchPos = new float[1536 * 12];
        private final int[] scratchColor = new int[1536 * 4];
        private float[] positions;
        private int[] colors;
        private int cursor, quads, tileQuads;
        public Job(TileSource source, int x, int z, int viewDistance, int radius, int quality, int quadLimit) {
            this.source = source; originChunkX = x; originChunkZ = z;
            this.radius = radius; this.quality = quality; this.quadLimit = quadLimit;
            preloadInner = Math.max(1, viewDistance - 3); fineRadius = viewDistance + 1;
            long[] temp = new long[(radius * 2 + 1) * (radius * 2 + 1)]; int n = 0;
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int d = dx * dx + dz * dz;
                if (d >= preloadInner * preloadInner && d <= radius * radius)
                    temp[n++] = ((long) d << 32) | ((long) (dx + 128) << 16) | (dz + 128);
            }
            offsets = Arrays.copyOf(temp, n); Arrays.sort(offsets);
            int initial = Math.min(quadLimit, 4096);
            positions = new float[initial * 12]; colors = new int[initial * 4];
        }
        public boolean done() { return cursor == offsets.length || quads == quadLimit; }
        public int quads() { return quads; }
        public void step(long budgetMicros, int tileBudget) {
            long end = System.nanoTime() + budgetMicros * 1000;
            int count = 0;
            while (!done() && count++ < tileBudget) {
                long o = offsets[cursor++]; int dx = (int) (o >> 16 & 65535) - 128, dz = (int) (o & 65535) - 128;
                TerrainTile tile = source.get(originChunkX + dx, originChunkZ + dz);
                if (tile != null) {
                    tileQuads = 0;
                    int s = stride((int) (o >>> 32), fineRadius, quality);
                    roofs(tile, dx, dz, s);
                    for (int dir = 0; dir < 4; dir++) walls(tile, dx, dz, s, dir);
                    if (quads + tileQuads <= quadLimit) {
                        capacity(quads + tileQuads);
                        System.arraycopy(scratchPos, 0, positions, quads * 12, tileQuads * 12);
                        System.arraycopy(scratchColor, 0, colors, quads * 4, tileQuads * 4);
                        quads += tileQuads;
                    }
                }
                if (System.nanoTime() >= end) break;
            }
        }
        public Built finish() {
            if (!done()) throw new IllegalStateException("unfinished mesh");
            // Transfer the reusable job's arrays without another multi-MiB copy.
            return new Built(positions, colors, quads);
        }
        private void capacity(int n) {
            if (n * 12 <= positions.length) return;
            int size = Math.min(quadLimit, Math.max(n, positions.length / 12 * 2));
            positions = Arrays.copyOf(positions, size * 12); colors = Arrays.copyOf(colors, size * 4);
        }
        private void roofs(TerrainTile t, int dx, int dz, int s) {
            Arrays.fill(used, false);
            for (int z = 0; z < 8; z += s) for (int x = 0; x < 8; x += s) {
                int i = z * 8 + x; if (used[i]) continue;
                short h = t.height(x, z, s); if (h == TerrainTile.EMPTY) continue;
                int c = t.color(x, z, s) & 0xFCFCFC, w = s, depth = s;
                while (x + w < 8 && matches(t, x + w, z, s, h, c)) w += s;
                outer: while (z + depth < 8) {
                    for (int xx = x; xx < x + w; xx += s)
                        if (!matches(t, xx, z + depth, s, h, c)) break outer;
                    depth += s;
                }
                for (int zz = z; zz < z + depth; zz += s) for (int xx = x; xx < x + w; xx += s) used[zz * 8 + xx] = true;
                float x0 = dx * 16 + x * 2, z0 = dz * 16 + z * 2, y = h - 0.035f;
                float relief = quality == 0 ? 1 : Math.max(0.90f, Math.min(1.02f, 0.96f + (h - 64) * 0.0006f));
                quad(x0,y,z0, x0,y,z0+depth*2, x0+w*2,y,z0+depth*2, x0+w*2,y,z0, shade(c,relief),shade(c,relief));
            }
        }
        private boolean matches(TerrainTile t, int x, int z, int s, int h, int c) {
            return !used[z * 8 + x] && t.height(x,z,s) == h && (t.color(x,z,s) & 0xFCFCFC) == c;
        }
        private void walls(TerrainTile t, int dx, int dz, int s, int dir) {
            int nx = dx + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
            int nz = dz + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
            // Exactly one cache lookup per cardinal direction, independent of rows/stride.
            TerrainTile border = source.get(originChunkX + nx, originChunkZ + nz);
            int bs = stride(nx * nx + nz * nz, fineRadius, quality);
            float light = switch (dir) { case 0 -> 0.70f; case 1 -> 0.86f; case 2 -> 0.64f; default -> 0.78f; };
            for (int row = 0; row < 8; row += s) {
                boolean edge = dir % 2 == 0 ? row == 0 : row + s == 8;
                int step = edge ? Math.min(s, bs) : s;
                int start = -1, lastH = 0, lastB = 0, lastC = 0, lastTop = 0;
                for (int u = 0; u <= 8; u += step) {
                    int h = TerrainTile.EMPTY, b = 0, c = 0, top = 0;
                    if (u < 8) {
                        int x = dir < 2 ? u : row, z = dir < 2 ? row : u;
                        h = t.height(x,z,s); c = t.side(x,z,s); top = t.color(x,z,s);
                        int nh;
                        if (edge) {
                            int bx = dir < 2 ? u : dir == 2 ? 7 : 0;
                            int bz = dir >= 2 ? u : dir == 0 ? 7 : 0;
                            nh = border == null ? TerrainTile.EMPTY : border.height(bx,bz,bs);
                        } else {
                            int bx = x + (dir == 2 ? -s : dir == 3 ? s : 0);
                            int bz = z + (dir == 0 ? -s : dir == 1 ? s : 0);
                            nh = t.height(bx,bz,s);
                        }
                        b = nh == TerrainTile.EMPTY ? h - 4 : nh;
                        if (h == TerrainTile.EMPTY || b >= h) h = TerrainTile.EMPTY;
                    }
                    boolean match = h != TerrainTile.EMPTY && h == lastH && b == lastB && c == lastC && top == lastTop;
                    if (start >= 0 && !match) {
                        strip(dx,dz,row,s,dir,start,u,lastH,lastB,lastC,lastTop,light); start = -1;
                    }
                    if (h != TerrainTile.EMPTY && start < 0) {
                        start = u; lastH = h; lastB = b; lastC = c; lastTop = top;
                    }
                }
            }
        }
        private void strip(int dx,int dz,int row,int s,int dir,int from,int to,int h,int bottom,int side,int top,float light) {
            float y = h - 0.035f, b = bottom - 0.035f;
            float lip = top != side ? Math.min(0.22f, y-b) : 0;
            wallQuad(dx,dz,row,s,dir,from,to,y-lip,b,side,light);
            if (lip > 0) wallQuad(dx,dz,row,s,dir,from,to,y,y-lip,top,light);
        }
        private void wallQuad(int dx,int dz,int row,int s,int dir,int from,int to,float y,float b,int c,float light) {
            int tc = shade(c, light * (quality >= 1 ? 1.03f : 1));
            int bc = shade(c, light * (quality >= 1 ? 0.76f : 1));
            float a = (dir < 2 ? dz : dx) * 16 + (row + (dir % 2 == 1 ? s : 0)) * 2;
            float lo = (dir < 2 ? dx : dz) * 16 + from * 2, hi = lo + (to-from)*2;
            if (dir == 0) quad(hi,y,a, hi,b,a, lo,b,a, lo,y,a,tc,bc);
            else if (dir == 1) quad(lo,y,a, lo,b,a, hi,b,a, hi,y,a,tc,bc);
            else if (dir == 2) quad(a,y,lo, a,b,lo, a,b,hi, a,y,hi,tc,bc);
            else quad(a,y,hi, a,b,hi, a,b,lo, a,y,lo,tc,bc);
        }
        private void quad(float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,int top,int bottom) {
            if (tileQuads >= scratchColor.length / 4) throw new IllegalStateException("tile scratch overflow");
            int p = tileQuads * 12, c = tileQuads++ * 4;
            scratchPos[p]=x0; scratchPos[p+1]=y0; scratchPos[p+2]=z0;
            scratchPos[p+3]=x1; scratchPos[p+4]=y1; scratchPos[p+5]=z1;
            scratchPos[p+6]=x2; scratchPos[p+7]=y2; scratchPos[p+8]=z2;
            scratchPos[p+9]=x3; scratchPos[p+10]=y3; scratchPos[p+11]=z3;
            scratchColor[c]=top; scratchColor[c+1]=bottom; scratchColor[c+2]=bottom; scratchColor[c+3]=top;
        }
    }
    public static int shade(int color, float factor) {
        int r = Math.min(255, (int) ((color >> 16 & 255) * factor));
        int g = Math.min(255, (int) ((color >> 8 & 255) * factor));
        int b = Math.min(255, (int) ((color & 255) * factor));
        return r << 16 | g << 8 | b;
    }
}
