package dev.pocketlod.cache;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** Serialized I/O prevents autosave, clear and disconnect writes from racing. */
public final class CacheIo implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"PocketLOD cache I/O"); t.setDaemon(true); return t;
    });
    public CompletableFuture<List<TileCache.Entry>> load(Path path,int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try { return CacheFile.read(path,limit); }
            catch (Exception e) { throw new CompletionException(e); }
        },worker);
    }
    public CompletableFuture<Void> save(Path path,List<TileCache.Entry> snapshot,int limit) {
        return CompletableFuture.runAsync(() -> {
            try { CacheFile.write(path,snapshot,limit); }
            catch (Exception e) { throw new CompletionException(e); }
        },worker);
    }
    public CompletableFuture<Void> delete(Path path) {
        return CompletableFuture.runAsync(() -> {
            try { java.nio.file.Files.deleteIfExists(path); }
            catch (Exception e) { throw new CompletionException(e); }
        },worker);
    }
    @Override public void close() {
        worker.shutdown();
        try { worker.awaitTermination(5,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
