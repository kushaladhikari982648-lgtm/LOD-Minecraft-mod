package dev.pocketlod.generation;

import dev.pocketlod.PocketLodClient;
import dev.pocketlod.core.TerrainTile;
import dev.pocketlod.mixin.ServerChunkManagerAccessor;
import dev.pocketlod.terrain.SurfaceCapture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** All ticket operations and protochunk capture occur on the integrated server thread. */
public final class DistantGenerator {
    public record Request(long session,RegistryKey<World> dimension,int x,int z,boolean features,Consumer<Result> sink) {}
    public record Result(long session,int x,int z,TerrainTile tile) {}
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("pocketlod",Comparator.comparingLong(ChunkPos::toLong));
    private final AtomicReference<Request> requested=new AtomicReference<>();
    private final SingleFlightChannel channel=new SingleFlightChannel();
    private Request active;
    private ServerWorld ticketWorld;
    private ChunkPos ticketPos;
    private int ticketLevel;
    private Vec3d lastPosition;
    private long started;
    public DistantGenerator() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { requested.set(null); cancel(); lastPosition=null; });
    }
    public boolean busy() { return requested.get()!=null || channel.busy(); }
    public void request(Request request) { if (!channel.busy()) requested.compareAndSet(null,request); }
    public void cancel(MinecraftServer server) { requested.set(null); if (server!=null) server.execute(this::cancel); }
    private void tick(MinecraftServer server) {
        var players=server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) { requested.set(null); cancel(); return; }
        var player=players.get(0); Vec3d position=player.getPos();
        boolean moving=lastPosition!=null && position.squaredDistanceTo(lastPosition)>4.0;
        // Reference is held until >2 blocks moved, so slow sustained walking also cancels.
        if (lastPosition==null || moving) lastPosition=position;
        if (moving) { requested.set(null); cancel(); return; }
        if (active!=null && (!active.dimension().equals(player.getWorld().getRegistryKey()) || System.nanoTime()-started>30_000_000_000L)) cancel();
        if (channel.busy()) return;
        Request request=requested.getAndSet(null);
        if (request==null || !request.dimension().equals(player.getWorld().getRegistryKey())) return;
        ServerWorld world=server.getWorld(request.dimension()); if (world==null) return;
        long token=channel.begin(); if (token<0) return;
        active=request; ticketWorld=world; ticketPos=new ChunkPos(request.x(),request.z()); started=System.nanoTime();
        ChunkStatus status=request.features()?ChunkStatus.FEATURES:ChunkStatus.SURFACE;
        ticketLevel=33+ChunkStatus.getDistanceFromFull(status);
        ServerChunkManagerAccessor manager=(ServerChunkManagerAccessor)world.getChunkManager();
        try {
            manager.pocketlod$tickets().addTicketWithLevel(TICKET,ticketPos,ticketLevel,ticketPos);
            manager.pocketlod$getChunkFuture(request.x(),request.z(),status,true).whenComplete((either,error) -> server.execute(() -> {
                if (!channel.accepts(token)) return;
                try {
                    if (error!=null) PocketLodClient.LOGGER.warn("Background chunk generation failed",error);
                    else either.left().ifPresent(chunk -> {
                        TerrainTile tile=SurfaceCapture.capture(chunk,request.features()?TerrainTile.FEATURES:TerrainTile.SURFACE);
                        request.sink().accept(new Result(request.session(),request.x(),request.z(),tile));
                    });
                } catch (RuntimeException e) { PocketLodClient.LOGGER.warn("Background capture failed",e); }
                finally { releaseTicket(); channel.finish(token); active=null; }
            }));
        } catch (RuntimeException e) { cancel(); PocketLodClient.LOGGER.warn("Unable to request distant chunk",e); }
    }
    private void releaseTicket() {
        if (ticketWorld!=null) ((ServerChunkManagerAccessor)ticketWorld.getChunkManager()).pocketlod$tickets().removeTicketWithLevel(TICKET,ticketPos,ticketLevel,ticketPos);
        ticketWorld=null; ticketPos=null;
    }
    private void cancel() { channel.cancel(); releaseTicket(); active=null; }
}
