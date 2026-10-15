package dev.pocketlod.core;

import dev.pocketlod.cache.*;
import dev.pocketlod.config.LodConfig;
import dev.pocketlod.generation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Dependency-light regression runner: use -ea. */
public final class CoreTests {
    private static int tests;
    private static void check(boolean ok,String name) { tests++; if (!ok) throw new AssertionError(name); }
    private static TerrainTile tile(int h,int color,int side) {
        short[] heights=new short[64]; int[] colors=new int[64],sides=new int[64];
        Arrays.fill(heights,(short)h); Arrays.fill(colors,color); Arrays.fill(sides,side);
        return new TerrainTile(heights,colors,sides,TerrainTile.FEATURES);
    }
    public static void main(String[] args) throws Exception {
        pyramid(); scan(); budgets(); channel(); cache(); mesh(); config(); persistence();
        System.out.println("PASS: "+tests+" PocketLOD core assertions");
    }
    private static void pyramid() {
        short[] h=new short[64]; int[] c=new int[64],s=new int[64];
        Arrays.fill(h,(short)64); Arrays.fill(c,0x00FF00); Arrays.fill(s,0x123456);
        h[0]=90; c[0]=0xFF0000;
        TerrainTile t=new TerrainTile(h,c,s,2);
        check(t.height(0,0,2)==64,"median rejects isolated tree");
        check(t.color(0,0,2)==0x00FF00,"median colors exclude tree");
        h[0]=0; c[0]=0;
        check(t.height(0,0,1)==90 && t.color(0,0,1)==0xFF0000,"defensive immutable copy");
        check(TerrainTile.index(7,7,1)==63 && TerrainTile.index(7,7,2)==79 && TerrainTile.index(7,7,4)==83 && TerrainTile.index(7,7,8)==84,"pyramid indexing");
        Arrays.fill(h,TerrainTile.EMPTY); t=new TerrainTile(h,c,s,1);
        check(t.height(0,0,8)==TerrainTile.EMPTY,"all empty coarse sample");
        h[63]=42; c[63]=0xAABBCC; s[63]=0x123456; t=new TerrainTile(h,c,s,1);
        check(t.height(0,0,8)==42 && t.color(0,0,8)==0xAABBCC && t.side(0,0,8)==0x123456,"empty values excluded");
        for (int i=0;i<64;i++) { h[i]=(short)(i%2==0?60:100); c[i]=i%2==0?0x00FF00:0xFF0000; }
        t=new TerrainTile(h,c,s,1); check(t.height(0,0,8)==100 && t.color(0,0,8)==0xFF0000,"no floating canopy average");
    }
    private static void scan() {
        AtomicInteger checks=new AtomicInteger();
        int top=SurfaceScan.findTop(319,-64,new SurfaceScan.Column() {
            public boolean emptySection(int y) { checks.incrementAndGet(); return y>=80; }
            public boolean surface(int y) { return y==67; }
        });
        check(top==67,"find top through empty sections"); check(checks.get()<40,"O(1) empty-section skip");
        check(SurfaceScan.findTop(-1,-64,new SurfaceScan.Column() {
            public boolean emptySection(int y) { return true; } public boolean surface(int y) { return false; }
        })==TerrainTile.EMPTY,"negative section scan terminates");
    }
    private static void budgets() {
        check(WorkBudget.generationDelayMillis(800,30,30,20)==800,"full speed pace");
        check(WorkBudget.generationDelayMillis(800,15,30,20)==1600,"half speed pace");
        check(WorkBudget.generationDelayMillis(800,1,30,20)==2400,"low FPS continues");
        check(WorkBudget.generationDelayMillis(800,1,30,141)==3200,"server lag continues");
        check(WorkBudget.meshBudgetMicros(900,1,true)>=1200 && WorkBudget.meshTilesPerFrame(8,1,true)>=8,"urgent mesh boosts");
        check(GenerationPolicy.allowed(true,true,true,128,128),"heap threshold inclusive");
        check(!GenerationPolicy.allowed(true,true,true,127,128),"heap pressure guard");
    }
    private static void channel() {
        SingleFlightChannel c=new SingleFlightChannel(); long a=c.begin();
        check(a>0 && c.begin()==-1,"one in flight"); c.cancel(); long b=c.begin();
        check(!c.accepts(a) && !c.finish(a) && c.accepts(b),"stale result rejected");
        check(c.finish(b) && !c.busy(),"channel finishes");
    }
    private static void cache() {
        TileCache c=new TileCache(256); TerrainTile t=tile(64,1,2);
        c.put(TileCache.key(-9,-123),t);
        check(c.get(-9,-123)==t && TileCache.x(TileCache.key(-9,-123))==-9 && TileCache.z(TileCache.key(-9,-123))==-123,"signed primitive keys");
        long v=c.version(); c.put(TileCache.key(-9,-123),tile(64,1,2));
        check(v==c.version(),"unchanged live capture stays clean");
        c.put(TileCache.key(-9,-123),new TerrainTile(new short[64],new int[64],new int[64],1));
        check(c.get(-9,-123)==t,"detail cannot downgrade");
        for (int i=0;i<300;i++) c.put(TileCache.key(i,0),t);
        check(c.size()==256 && c.get(0,0)==null && c.get(299,0)!=null,"bounded cache eviction");
        CaptureQueue q=new CaptureQueue(); q.add(1);q.add(1);q.add(2);
        check(q.poll()==1 && q.poll()==2 && q.isEmpty(),"primitive capture FIFO deduplicates");
        GenerationPlanner plan=new GenerationPlanner(); c.clear(); plan.plan(0,0,6,2,c); int n=0;
        while (!plan.isEmpty()) { long k=plan.poll(); int x=TileCache.x(k),z=TileCache.z(k); check(x*x+z*z<=36,"generation circular plan"); n++; }
        check(n==113,"generation circle population");
    }
    private static SurfaceMesher.Built build(SurfaceMesher.TileSource source,int view,int radius,int quality,int limit) {
        var job=new SurfaceMesher.Job(source,0,0,view,radius,quality,limit);
        int steps=0; while (!job.done() && steps++<100000) job.step(100,2);
        check(job.done(),"incremental mesh completes"); return job.finish();
    }
    private static void mesh() {
        TerrainTile flat=tile(64,0x77AA55,0x77AA55); AtomicInteger calls=new AtomicInteger();
        SurfaceMesher.TileSource source=(x,z) -> { calls.incrementAndGet(); return x==1 && z==0?flat:null; };
        var b=build(source,4,1,2,1000);
        check(b.quads==5,"greedy flat roof plus four skirts");
        check(calls.get()==8,"four border lookups for one present tile plus candidate lookups");
        int roofs=0;
        for (int q=0;q<b.quads;q++) {
            int p=q*12; boolean roof=b.positions[p+1]==b.positions[p+4] && b.positions[p+1]==b.positions[p+7] && b.positions[p+1]==b.positions[p+10];
            if (roof) roofs++;
            else {
                boolean axis=b.positions[p]==b.positions[p+3] && b.positions[p]==b.positions[p+6] && b.positions[p]==b.positions[p+9]
                        || b.positions[p+2]==b.positions[p+5] && b.positions[p+2]==b.positions[p+8] && b.positions[p+2]==b.positions[p+11];
                check(axis,"wall is axis aligned");
                check(Math.abs(b.positions[p+1]-b.positions[p+4]-4)<0.001,"unknown edge 4-block skirt");
            }
        }
        check(roofs==1,"one 2D merged roof");
        check(build(source,4,1,2,4).quads==0,"whole-tile quad-limit atomicity");
        var lip=build((x,z) -> x==1 && z==0?tile(64,0x00FF00,0x79553A):null,4,1,2,1000);
        check(lip.quads==9,"cliffs have separate surface lips");
        var hidden=build((x,z) -> flat,4,1,2,1000);
        check(hidden.quads==4,"equal-height neighbors hide every cliff");
        check(SurfaceMesher.stride(20*20,10,2)==1 && SurfaceMesher.stride(21*21,10,2)==2 && SurfaceMesher.stride(35*35,10,2)==4,"HIGH detail rings");
        check(SurfaceMesher.stride(28*28,10,3)==1 && SurfaceMesher.stride(50*50,10,3)==2,"ULTRA detail rings");
        check(build((x,z) -> x==0 && z==0?flat:null,8,16,2,1000).quads==0,"circular preload center excluded");
        check(build((x,z) -> x==6 && z==6?flat:null,8,16,2,1000).quads>0,"diagonal vanilla corner covered");
        Random r=new Random(4); short[] h=new short[64]; int[] c=new int[64],s=new int[64];
        for (int i=0;i<64;i++) { h[i]=(short)(40+r.nextInt(60));c[i]=r.nextInt(1<<24);s[i]=r.nextInt(1<<24); }
        var rough=new TerrainTile(h,c,s,2);
        check(build((x,z) -> x*x+z*z<=4?rough:null,4,8,3,5000).quads<=5000,"rough tiles respect cap");
    }
    private static void config() throws Exception {
        LodConfig c=new LodConfig(); c.validate();
        check(c.lodRadiusChunks==32 && c.maxCachedChunks==8192 && c.maxQuads==49152 && c.graphicsQuality==2,"mobile defaults");
        c.preset("lowendpc"); c.validate(); check(c.lodRadiusChunks==48 && c.maxQuads==81920 && c.generationIntervalMillis==500,"PC preset");
        c.preset("ultra");c.validate();check(c.maxCachedChunks==24576 && c.graphicsQuality==3,"ultra preset");
        c.maxCachedChunks=256;c.maxQuads=1024;c.lodRadiusChunks=96;c.generationRadiusChunks=64;c.ensureCapacityForRadius();
        check(c.maxCachedChunks>=28952 && c.maxCachedChunks<=32768 && c.maxQuads==131072,"custom radius capacity scaling");
        c.lodRadiusChunks=-100;c.generationRadiusChunks=100;c.graphicsQuality=100;c.validate();
        check(c.lodRadiusChunks==8 && c.generationRadiusChunks==64 && c.graphicsQuality==3,"config clamps");
        Path dir=Files.createTempDirectory("pocketlod-config");
        try { Path p=dir.resolve("config.json");c.save(p);check(LodConfig.load(p).generationRadiusChunks==64,"config round trip");Files.writeString(p,"null");check(LodConfig.load(p).lodRadiusChunks==32,"null config defaults"); }
        finally { try(var files=Files.list(dir)) { for(Path p:files.toList()) Files.delete(p); } Files.delete(dir); }
    }
    private static void persistence() throws Exception {
        Path dir=Files.createTempDirectory("pocketlod-tests");
        try {
            Path file=dir.resolve("cache.plod");var t=tile(72,0x123456,0x654321);
            CacheFile.write(file,List.of(new TileCache.Entry(TileCache.key(-17,44),t)),48);
            check(Files.size(file)==12+649+4,"v2 record size");
            var entries=CacheFile.read(file,48);
            check(entries.size()==1 && entries.get(0).key()==TileCache.key(-17,44) && entries.get(0).tile().side(0,0,1)==0x654321,"checksummed v2 round trip");
            byte[] payload=Files.readAllBytes(file);payload[20]^=1;Files.write(file,payload);
            boolean failed=false;try{CacheFile.read(file,48);}catch(IOException e){failed=true;}check(failed,"corrupt record rejected");
            Files.write(file,Arrays.copyOf(payload,30));failed=false;try{CacheFile.read(file,48);}catch(IOException e){failed=true;}check(failed,"truncation rejected");
            ByteArrayOutputStream record=new ByteArrayOutputStream();var rec=new DataOutputStream(record);
            rec.writeLong(123);for(int i=0;i<16;i++) rec.writeShort(64+i);for(int i=0;i<16;i++) rec.writeInt(0x8899AA);
            byte[] data=record.toByteArray();CRC32 crc=new CRC32();crc.update(data);
            try(var out=new DataOutputStream(Files.newOutputStream(file))) {out.writeInt(CacheFile.MAGIC);out.writeInt(1);out.writeInt(1);out.write(data);out.writeInt((int)crc.getValue());}
            var old=CacheFile.read(file,48).get(0).tile();
            check(old.detail==TerrainTile.LEGACY && old.height(7,7,1)==79 && old.height(6,6,1)==79 && old.side(0,0,1)==0x8899AA,"v1 4x4 migration");
            List<TileCache.Entry> many=new ArrayList<>();for(int i=0;i<2000;i++)many.add(new TileCache.Entry(i,t));
            CacheFile.write(file,many,1);check(Files.size(file)<=1048576 && CacheFile.read(file,1).size()<2000,"disk cap retains newest records");
        } finally {try(var files=Files.list(dir)){for(Path p:files.toList())Files.delete(p);}Files.delete(dir);}
    }
}
