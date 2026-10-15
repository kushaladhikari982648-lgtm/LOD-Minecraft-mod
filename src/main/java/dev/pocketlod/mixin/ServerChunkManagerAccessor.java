package dev.pocketlod.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.*;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessor {
    @Accessor("ticketManager") ChunkTicketManager pocketlod$tickets();
    @Invoker("getChunkFuture") CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>> pocketlod$getChunkFuture(int x,int z,ChunkStatus status,boolean create);
}
