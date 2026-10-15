package dev.pocketlod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pocketlod.LodEngine;
import dev.pocketlod.config.LodConfig;
import dev.pocketlod.core.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Client render-thread ownership for CPU mesh jobs, uniforms and dynamic VBOs. */
public final class LodRenderer implements AutoCloseable {
    public static ShaderProgram shader;
    private final LodEngine engine;
    private LodMesh mesh;
    private SurfaceMesher.Job job;
    private SurfaceMesher.Built pending;
    private long jobVersion,builtVersion=-1;
    private int originX,originZ,builtPreloadInner=1,builtQuality=-1,builtRadius,builtView;
    private int jobView,jobLimit,builtLimit;
    private long lastBuildTick;
    private float smoothedFogDistance;
    private long lastFrame;
    public LodRenderer(LodEngine engine) { this.engine=engine; }
    public void render(WorldRenderContext context) {
        MinecraftClient client=MinecraftClient.getInstance(); LodConfig c=engine.config();
        if (!c.enabled || client.world==null || client.player==null || shader==null || context.matrixStack()==null || engine.shaderPackActive()) return;
        Vec3d camera=context.camera().getPos();
        if (c.hideCaves && camera.y<client.world.getBottomY()+16) return;
        if (c.hideCaves && !client.world.isSkyVisible(client.player.getBlockPos()) && camera.y<client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,client.player.getBlockX(),client.player.getBlockZ())-8) return;
        int cx=((int)Math.floor(camera.x))>>4,cz=((int)Math.floor(camera.z))>>4;
        int vd=client.options.getViewDistance().getValue();
        int drift=Math.max(Math.abs(cx-originX),Math.abs(cz-originZ));
        boolean visible=mesh!=null && mesh.quads()>0 && drift<=Math.max(8,c.lodRadiusChunks/2);
        boolean urgent=drift>=2 || builtVersion<0 || builtQuality!=c.graphicsQuality;
        if (job!=null && (Math.max(Math.abs(cx-job.originChunkX),Math.abs(cz-job.originChunkZ))>=3
                || job.quality!=c.graphicsQuality || job.radius!=c.lodRadiusChunks || jobView!=vd || jobLimit!=c.maxQuads)) {
            job=null; pending=null;
        }
        boolean uploaded=false;
        if (pending!=null) { commit(); uploaded=true; }
        if (!uploaded && job==null && (urgent || engine.ticks()-lastBuildTick>=c.rebuildIntervalTicks)
                && (builtVersion!=engine.cache().version() || drift>=1 || builtQuality!=c.graphicsQuality
                || builtRadius!=c.lodRadiusChunks || builtView!=vd || builtLimit!=c.maxQuads)) {
            job=new SurfaceMesher.Job(engine.cache(),cx,cz,vd,c.lodRadiusChunks,c.graphicsQuality,c.maxQuads);
            jobVersion=engine.cache().version(); jobView=vd; jobLimit=c.maxQuads;
        }
        if (!uploaded && job!=null) {
            int fps=Math.max(1,client.getCurrentFps()); long start=System.nanoTime();
            job.step(WorkBudget.meshBudgetMicros(c.meshBudgetMicros,fps,urgent),WorkBudget.meshTilesPerFrame(c.meshTilesPerFrame,fps,urgent));
            if (job.done()) {
                pending=job.finish();
                if (!visible || System.nanoTime()-start<=350_000L) { commit(); uploaded=true; }
            }
        }
        drift=Math.max(Math.abs(cx-originX),Math.abs(cz-originZ));
        if (mesh==null || drift>Math.max(8,c.lodRadiusChunks/2)) return;
        long now=System.nanoTime();
        float target=c.lodRadiusChunks*16f;
        if (smoothedFogDistance==0) smoothedFogDistance=target;
        double dt=lastFrame==0?0.016:Math.min(0.25,(now-lastFrame)/1e9); lastFrame=now;
        smoothedFogDistance+=(target-smoothedFogDistance)*(float)(1-Math.exp(-dt*2));
        float near=Math.max(builtPreloadInner*16f+6f,(vd-1.5f)*16f);
        double nx=camera.x-originX*16.0,nz=camera.z-originZ*16.0;
        double dx=nx-8,dz=nz-8,len=Math.sqrt(dx*dx+dz*dz);
        float max=Math.max(0,near-builtPreloadInner*16f+2f);
        if (len>max && len>0) { nx=8+dx*max/len; nz=8+dz*max/len; }
        shader.getUniformOrDefault("NearCenter").set((float)nx,(float)nz);
        shader.getUniformOrDefault("NearHalfWidth").set(near);
        // Keep grain coordinates small and exact even at the world border; the hash is periodic.
        shader.getUniformOrDefault("GrainOrigin").set(Math.floorMod(originX,256)*16f,0,Math.floorMod(originZ,256)*16f);
        Matrix4f model=new Matrix4f(context.matrixStack().peek().getPositionMatrix())
                .translate((float)(originX*16.0-camera.x),(float)-camera.y,(float)(originZ*16.0-camera.z));
        ShaderProgram previous=RenderSystem.getShader();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); RenderSystem.disableCull();
        RenderSystem.polygonOffset(1.0f,3.0f); RenderSystem.enablePolygonOffset();
        try { RenderSystem.setShader(() -> shader); mesh.draw(model,context.projectionMatrix(),shader); }
        finally {
            RenderSystem.disablePolygonOffset(); RenderSystem.polygonOffset(0,0); RenderSystem.enableCull();
            RenderSystem.setShader(() -> previous);
        }
    }
    private void commit() {
        if (mesh==null) mesh=new LodMesh();
        mesh.upload(pending); originX=job.originChunkX; originZ=job.originChunkZ;
        builtPreloadInner=job.preloadInner; builtQuality=job.quality; builtRadius=job.radius;
        builtView=jobView; builtLimit=jobLimit; builtVersion=jobVersion;
        lastBuildTick=engine.ticks(); pending=null; job=null;
    }
    public float fogDistance() { return smoothedFogDistance>0?smoothedFogDistance:engine.config().lodRadiusChunks*16f; }
    public boolean active() {
        MinecraftClient client=MinecraftClient.getInstance(); LodConfig c=engine.config();
        if (!c.enabled || mesh==null || mesh.quads()==0 || engine.shaderPackActive() || client.world==null || client.player==null) return false;
        Vec3d camera=client.gameRenderer.getCamera().getPos();
        int cx=((int)Math.floor(camera.x))>>4,cz=((int)Math.floor(camera.z))>>4;
        if (Math.max(Math.abs(cx-originX),Math.abs(cz-originZ))>Math.max(8,c.lodRadiusChunks/2)) return false;
        return !c.hideCaves || (camera.y>=client.world.getBottomY()+16 &&
                (client.world.isSkyVisible(client.player.getBlockPos()) || camera.y>=client.world.getTopY(
                        net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,client.player.getBlockX(),client.player.getBlockZ())-8));
    }
    public int quads() { return mesh==null?0:mesh.quads(); }
    public void invalidate() { builtVersion=-1; job=null; pending=null; }
    @Override public void close() {
        if (mesh!=null) mesh.close(); mesh=null; job=null; pending=null; builtVersion=-1;
        builtQuality=-1; smoothedFogDistance=0; lastFrame=0;
    }
}
