package dev.pocketlod;

import dev.pocketlod.cache.*;
import dev.pocketlod.config.LodConfig;
import dev.pocketlod.core.*;
import dev.pocketlod.generation.*;
import dev.pocketlod.render.LodRenderer;
import dev.pocketlod.terrain.SurfaceCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Client-thread-owned cache/session coordinator; background results are marshalled back here. */
public final class LodEngine implements AutoCloseable {
    private final LodConfig config;
    private final Path configPath,cacheRoot;
    private final TileCache cache;
    private final CaptureQueue captureQueue=new CaptureQueue();
    private final GenerationPlanner planner=new GenerationPlanner();
    private final CacheIo io=new CacheIo();
    private final DistantGenerator generator=new DistantGenerator();
    private final LodRenderer renderer;
    private final Method irisInstance,irisEnabled;
    private ClientWorld world;
    private Path cachePath;
    private long session,ticks,lastSavedVersion=-1,nextGeneration;
    private boolean saving,loading,shaderPackActive;
    private Vec3d lastPosition;
    private long stationarySince;
    private int captureCursor;
    public LodEngine(LodConfig config,Path configPath,Path cacheRoot) {
        this.config=config; this.configPath=configPath; this.cacheRoot=cacheRoot;
        cache=new TileCache(config.maxCachedChunks); renderer=new LodRenderer(this);
        Method instance=null,enabled=null;
        for (String name:new String[]{"net.irisshaders.iris.api.v0.IrisApi","net.coderbot.iris.api.v0.IrisApi"}) {
            try { Class<?> iris=Class.forName(name); instance=iris.getMethod("getInstance"); enabled=iris.getMethod("isShaderPackInUse"); break; }
            catch (ReflectiveOperationException ignored) {}
        }
        irisInstance=instance; irisEnabled=enabled;
    }
    public LodConfig config() { return config; }
    public TileCache cache() { return cache; }
    public LodRenderer renderer() { return renderer; }
    public long ticks() { return ticks; }
    public boolean shaderPackActive() { return shaderPackActive; }
    private void updateIris() {
        if (irisInstance==null) return;
        try { shaderPackActive=(boolean)irisEnabled.invoke(irisInstance.invoke(null)); }
        catch (ReflectiveOperationException e) { shaderPackActive=true; }
    }
    public void chunkLoaded(ClientWorld world,WorldChunk chunk) {
        attach(world); captureQueue.add(chunk.getPos().toLong());
    }
    private void attach(ClientWorld newWorld) {
        if (newWorld==world) return;
        save(true); session++; generator.cancel(MinecraftClient.getInstance().getServer());
        world=newWorld; cache.clear(); captureQueue.clear(); planner.clear(); renderer.close();
        lastSavedVersion=-1; saving=false; loading=false; lastPosition=null; captureCursor=0;
        nextGeneration=0; cachePath=null;
        if (world==null) return;
        MinecraftClient client=MinecraftClient.getInstance();
        String identity=client.getServer()!=null ? "local:"+client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()
                : client.getCurrentServerEntry()!=null ? "remote:"+client.getCurrentServerEntry().address : "remote:unknown";
        String dimension=world.getRegistryKey().getValue().toString();
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((identity+"|"+dimension).getBytes(StandardCharsets.UTF_8)));
            cachePath=cacheRoot.resolve(hash+".plod");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        long loadSession=session,clearVersion=cache.version(); loading=true;
        io.load(cachePath,config.diskCacheMiB).whenComplete((entries,error) -> client.execute(() -> {
            if (session!=loadSession) return;
            loading=false;
            if (error!=null) { PocketLodClient.LOGGER.warn("Unable to read terrain cache (will recapture)",error); return; }
            boolean untouched=cache.version()==clearVersion;
            for (var entry:entries) if (cache.get(entry.key())==null) cache.put(entry.key(),entry.tile());
            if (untouched) lastSavedVersion=cache.version();
        }));
    }
    public void tick(MinecraftClient client) {
        ticks++; attach(client.world);
        if (ticks%20==0) updateIris();
        if (world==null || client.player==null) return;
        cache.setLimit(config.maxCachedChunks);
        if (config.enabled) {
            // Incremental live refresh handles edits and trees without a per-frame full-radius sweep.
            int vd=client.options.getViewDistance().getValue(),side=vd*2+1;
            for (int n=0;n<2;n++) {
                int i=captureCursor++%(side*side),x=client.player.getChunkPos().x+i%side-vd,z=client.player.getChunkPos().z+i/side-vd;
                WorldChunk chunk=world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false);
                if (chunk!=null && !(chunk instanceof EmptyChunk)) captureQueue.add(TileCache.key(x,z));
            }
            long end=System.nanoTime()+900_000L;
            for (int n=0;n<2 && !captureQueue.isEmpty();n++) {
                long key=captureQueue.poll();
                WorldChunk chunk=world.getChunkManager().getChunk(TileCache.x(key),TileCache.z(key),ChunkStatus.FULL,false);
                if (chunk!=null && !(chunk instanceof EmptyChunk)) {
                    try { cache.put(key,SurfaceCapture.capture(chunk,TerrainTile.FEATURES)); }
                    catch (RuntimeException e) { PocketLodClient.LOGGER.warn("Live terrain capture failed",e); }
                }
                if (System.nanoTime()>=end) break;
            }
            generate(client);
        } else generator.cancel(client.getServer());
        if (ticks%1200==0) save(false);
    }
    private void generate(MinecraftClient client) {
        Vec3d position=client.player.getPos(); long now=System.currentTimeMillis();
        if (lastPosition==null || position.squaredDistanceTo(lastPosition)>4) {
            lastPosition=position; stationarySince=now; generator.cancel(client.getServer());
            planner.clear(); return;
        }
        Runtime rt=Runtime.getRuntime(); long free=(rt.maxMemory()-rt.totalMemory()+rt.freeMemory())>>20;
        if (!GenerationPolicy.allowed(config.singleplayerGeneration,client.getServer()!=null,now-stationarySince>=1000,
                free,config.generationMinimumFreeHeapMiB)) {
            if (!config.singleplayerGeneration) generator.cancel(client.getServer()); return;
        }
        if (generator.busy() || now<nextGeneration || loading) return;
        var cp=client.player.getChunkPos();
        planner.plan(cp.x,cp.z,config.generationRadiusChunks,config.generateFeatures?TerrainTile.FEATURES:TerrainTile.SURFACE,cache);
        if (planner.isEmpty()) { nextGeneration=now+2000; return; }
        long key=0;
        boolean found=false;
        int required=config.generateFeatures?TerrainTile.FEATURES:TerrainTile.SURFACE;
        while (!planner.isEmpty()) {
            key=planner.poll(); TerrainTile tile=cache.get(key);
            if (tile==null || tile.detail<required) { found=true; break; }
        }
        if (!found) { nextGeneration=now+2000; return; }
        long requestSession=session;
        generator.request(new DistantGenerator.Request(session,world.getRegistryKey(),TileCache.x(key),TileCache.z(key),config.generateFeatures,
                result -> client.execute(() -> {
                    if (session==requestSession && result.session()==session)
                        cache.put(TileCache.key(result.x(),result.z()),result.tile());
                })));
        nextGeneration=now+WorkBudget.generationDelayMillis(config.generationIntervalMillis,client.getCurrentFps(),config.targetFps,client.getServer().getTickTime());
    }
    public void changed() {
        config.validate(); cache.setLimit(config.maxCachedChunks); planner.clear(); renderer.invalidate();
        if (!config.singleplayerGeneration) generator.cancel(MinecraftClient.getInstance().getServer());
        try { config.save(configPath); }
        catch (java.io.IOException e) { PocketLodClient.LOGGER.warn("Could not save settings",e); }
    }
    private void save(boolean force) {
        if (cachePath==null || (!force && saving) || loading || cache.version()==lastSavedVersion) return;
        saving=true; long version=cache.version(),saveSession=session;
        io.save(cachePath,cache.snapshot(),config.diskCacheMiB).whenComplete((unused,error) -> MinecraftClient.getInstance().execute(() -> {
            if (session!=saveSession) return;
            saving=false;
            if (error==null) lastSavedVersion=version;
            else PocketLodClient.LOGGER.warn("Could not save terrain cache",error);
        }));
    }
    public void clear() {
        session++; generator.cancel(MinecraftClient.getInstance().getServer());
        cache.clear(); captureQueue.clear(); planner.clear(); renderer.close(); loading=false; saving=false;
        lastSavedVersion=-1;
        if (cachePath!=null) io.delete(cachePath).exceptionally(error -> { PocketLodClient.LOGGER.warn("Could not delete terrain cache",error); return null; });
    }
    public String status() {
        return "PocketLOD 0.4.0: "+(config.enabled?"ON":"OFF")+" | "+config.lodRadiusChunks+"c ("+config.lodRadiusChunks*16+"m) | "+cache.size()+" tiles | "+renderer.quads()+" quads | generation "+(config.singleplayerGeneration?"ON":"OFF")+(shaderPackActive?" | paused: Iris shader pack":"");
    }
    @Override public void close() { save(true); generator.cancel(MinecraftClient.getInstance().getServer()); renderer.close(); io.close(); }
}
