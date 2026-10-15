package dev.pocketlod.core;

import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import java.util.IdentityHashMap;
import java.util.Map;

/** Approximate 1.20.1 texture averages, not map-color palette colors. */
public final class TerrainColors {
    private static final Map<Block,Integer> COLORS = new IdentityHashMap<>();
    static {
        put(0x79553A,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.ROOTED_DIRT);
        put(0x5B3F28,Blocks.PODZOL); put(0x706364,Blocks.MYCELIUM);
        put(0x947B48,Blocks.DIRT_PATH); put(0x72503A,Blocks.FARMLAND);
        put(0x596D2C,Blocks.MOSS_BLOCK,Blocks.MOSS_CARPET);
        put(0x3C393E,Blocks.MUD); put(0x8A6850,Blocks.PACKED_MUD);
        put(0x7D7D7D,Blocks.STONE,Blocks.COBBLESTONE);
        put(0x505052,Blocks.DEEPSLATE,Blocks.COBBLED_DEEPSLATE);
        put(0x888888,Blocks.ANDESITE); put(0xBDBDBD,Blocks.DIORITE);
        put(0x956755,Blocks.GRANITE); put(0x6C6D65,Blocks.TUFF);
        put(0xDFDFD5,Blocks.CALCITE); put(0x837F7E,Blocks.GRAVEL);
        put(0xA0A6B4,Blocks.CLAY); put(0xDBCFA3,Blocks.SAND,Blocks.SANDSTONE);
        put(0xBE6721,Blocks.RED_SAND,Blocks.RED_SANDSTONE);
        put(0x985E43,Blocks.TERRACOTTA); put(0xEFF4F4,Blocks.SNOW,Blocks.SNOW_BLOCK);
        put(0x8EAFCF,Blocks.ICE); put(0x8CAED6,Blocks.PACKED_ICE);
        put(0x74A7FA,Blocks.BLUE_ICE); put(0xE66B15,Blocks.LAVA);
        put(0xE5ADC2,Blocks.CHERRY_LEAVES); put(0x5A732C,Blocks.AZALEA_LEAVES);
        put(0x646F3D,Blocks.FLOWERING_AZALEA_LEAVES);
        put(0x6C3435,Blocks.NETHERRACK); put(0x42382A,Blocks.SOUL_SAND,Blocks.SOUL_SOIL);
        put(0xDBDDA0,Blocks.END_STONE); put(0x302A3D,Blocks.OBSIDIAN);
    }
    private TerrainColors() {}
    private static void put(int color,Block... blocks) { for (Block block:blocks) COLORS.put(block,color); }
    public static int tint(int biome,int texture) {
        return (((biome>>16&255)*(texture>>16&255)/255)<<16)
                | (((biome>>8&255)*(texture>>8&255)/255)<<8) | ((biome&255)*(texture&255)/255);
    }
    public static int color(BlockState state,int grass,int foliage,int water) {
        if (state.getFluidState().isIn(FluidTags.WATER)) return tint(water,0xD6D6D6);
        Integer explicit = COLORS.get(state.getBlock()); if (explicit != null) return explicit;
        if (state.isOf(Blocks.GRASS_BLOCK)) return tint(grass,0x969696);
        if (state.isIn(BlockTags.LEAVES)) return tint(foliage,0x929292);
        return state.getBlock().getDefaultMapColor().color;
    }
    public static int blend(int a,int b,float amount) {
        float remain = 1-amount;
        return ((int)((a>>16&255)*remain+(b>>16&255)*amount)<<16)
                | ((int)((a>>8&255)*remain+(b>>8&255)*amount)<<8)
                | (int)((a&255)*remain+(b&255)*amount);
    }
}
