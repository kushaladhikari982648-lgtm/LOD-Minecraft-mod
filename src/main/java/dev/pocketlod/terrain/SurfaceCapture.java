package dev.pocketlod.terrain;

import dev.pocketlod.core.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.*;
import java.util.Arrays;

/** A capture only reads this chunk: safe for live chunks and SURFACE/FEATURES protochunks. */
public final class SurfaceCapture {
    private SurfaceCapture() {}
    public static TerrainTile capture(Chunk chunk,int detail) {
        short[] heights = new short[64]; int[] colors = new int[64], sides = new int[64];
        Arrays.fill(heights,TerrainTile.EMPTY);
        int[] grass = new int[16], foliage = new int[16], water = new int[16];
        boolean[] sampled = new boolean[16];
        Column column = new Column(chunk);
        Heightmap.Type map = chunk.getStatus() == ChunkStatus.FULL && chunk.hasHeightmap(Heightmap.Type.MOTION_BLOCKING)
                ? Heightmap.Type.MOTION_BLOCKING : chunk.hasHeightmap(Heightmap.Type.WORLD_SURFACE)
                ? Heightmap.Type.WORLD_SURFACE : chunk.hasHeightmap(Heightmap.Type.WORLD_SURFACE_WG)
                ? Heightmap.Type.WORLD_SURFACE_WG : null;
        for (int z=0;z<8;z++) for (int x=0;x<8;x++) {
            column.x=x*2; column.z=z*2;
            int start = map == null ? chunk.getTopY()-1 : Math.min(chunk.getTopY()-1,chunk.sampleHeightmap(map,column.x,column.z)+1);
            int y = SurfaceScan.findTop(start,chunk.getBottomY(),column);
            if (y == TerrainTile.EMPTY) continue;
            int idx=z*8+x, biomeIdx=(z>>1)*4+(x>>1);
            if (!sampled[biomeIdx]) {
                int wx=chunk.getPos().getStartX()+column.x, wz=chunk.getPos().getStartZ()+column.z;
                var biome=chunk.getBiomeForNoiseGen(wx>>2,y>>2,wz>>2).value();
                grass[biomeIdx]=biome.getGrassColorAt(wx,wz); foliage[biomeIdx]=biome.getFoliageColor(); water[biomeIdx]=biome.getWaterColor();
                sampled[biomeIdx]=true;
            }
            BlockState state=column.state(y);
            int c=TerrainColors.color(state,grass[biomeIdx],foliage[biomeIdx],water[biomeIdx]);
            if (state.getFluidState().isIn(FluidTags.WATER)) {
                BlockState shallow=column.state(y-2);
                if (shallow.isOpaque() && shallow.getFluidState().isEmpty())
                    c=TerrainColors.blend(c,TerrainColors.color(shallow,grass[biomeIdx],foliage[biomeIdx],water[biomeIdx]),0.22f);
                else if (column.state(y-5).getFluidState().isIn(FluidTags.WATER)) c=SurfaceMesher.shade(c,0.88f);
            }
            int side=c;
            if (!state.isIn(BlockTags.LEAVES) && state.getFluidState().isEmpty()) {
                BlockState below=column.state(y-1);
                if (!below.isAir()) side=TerrainColors.color(below,grass[biomeIdx],foliage[biomeIdx],water[biomeIdx]);
            }
            heights[idx]=(short)(y+1); colors[idx]=c; sides[idx]=side;
        }
        return new TerrainTile(heights,colors,sides,detail);
    }
    private static final class Column implements SurfaceScan.Column {
        final Chunk chunk; final ChunkSection[] sections; final BlockPos.Mutable pos=new BlockPos.Mutable();
        int x,z;
        Column(Chunk chunk) { this.chunk=chunk; sections=chunk.getSectionArray(); }
        ChunkSection section(int y) {
            int idx=chunk.getSectionIndex(y);
            return idx<0 || idx>=sections.length ? null : sections[idx];
        }
        BlockState state(int y) {
            ChunkSection s=section(y);
            return s==null || s.isEmpty() ? Blocks.AIR.getDefaultState() : s.getBlockState(x,y&15,z);
        }
        @Override public boolean emptySection(int y) { ChunkSection s=section(y); return s==null || s.isEmpty(); }
        @Override public boolean surface(int y) {
            BlockState state=state(y);
            if (state.isAir()) return false;
            if (!state.getFluidState().isEmpty() || state.isOf(Blocks.SNOW) || state.isOpaque()) return true;
            pos.set(chunk.getPos().getStartX()+x,y,chunk.getPos().getStartZ()+z);
            return !state.getCollisionShape(chunk,pos).isEmpty();
        }
    }
}
