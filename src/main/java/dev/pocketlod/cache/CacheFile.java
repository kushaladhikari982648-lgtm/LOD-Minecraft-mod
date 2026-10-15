package dev.pocketlod.cache;

import dev.pocketlod.core.TerrainTile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Big-endian records; CRC32 is outside the 649-byte v2 payload. */
public final class CacheFile {
    public static final int MAGIC = 0x504C4431, VERSION = 2, RECORD_BYTES = 649, MAX_TILES = 32768;
    private static final int V1_BYTES = 104;
    private CacheFile() {}
    public static List<TileCache.Entry> read(Path file, int diskMiB) throws IOException {
        if (!Files.exists(file)) return List.of();
        long size = Files.size(file);
        if (size > diskMiB * 1048576L || size > 12L + MAX_TILES * (RECORD_BYTES + 4L))
            throw new IOException("Cache exceeds size limit");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid cache magic");
            int version = in.readInt(), count = in.readInt();
            if (version != 1 && version != VERSION) throw new IOException("Unsupported cache version");
            if (count < 0 || count > MAX_TILES) throw new IOException("Invalid tile count");
            int bytes = version == 1 ? V1_BYTES : RECORD_BYTES;
            if (size != 12L + count * (bytes + 4L)) throw new IOException("Truncated/trailing cache data");
            List<TileCache.Entry> out = new ArrayList<>(count);
            byte[] payload = new byte[bytes]; CRC32 crc = new CRC32();
            for (int n = 0; n < count; n++) {
                in.readFully(payload); int checksum = in.readInt();
                crc.reset(); crc.update(payload);
                if ((int) crc.getValue() != checksum) throw new IOException("Cache checksum mismatch at record " + n);
                DataInputStream record = new DataInputStream(new ByteArrayInputStream(payload));
                long key = record.readLong(); int detail = version == 1 ? TerrainTile.LEGACY : record.readUnsignedByte();
                if (detail > TerrainTile.FEATURES) throw new IOException("Invalid terrain detail");
                short[] h = new short[64]; int[] c = new int[64], s = new int[64];
                if (version == 2) {
                    for (int i=0;i<64;i++) h[i]=record.readShort();
                    for (int i=0;i<64;i++) c[i]=record.readInt();
                    for (int i=0;i<64;i++) s[i]=record.readInt();
                } else {
                    short[] oldH = new short[16]; int[] oldC = new int[16];
                    for (int i=0;i<16;i++) oldH[i]=record.readShort();
                    for (int i=0;i<16;i++) oldC[i]=record.readInt();
                    for (int z=0;z<8;z++) for (int x=0;x<8;x++) {
                        int i=z*8+x, old=(z>>1)*4+(x>>1); h[i]=oldH[old]; c[i]=s[i]=oldC[old];
                    }
                }
                out.add(new TileCache.Entry(key,new TerrainTile(h,c,s,detail)));
            }
            return out;
        }
    }
    public static void write(Path file, List<TileCache.Entry> tiles, int diskMiB) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        int capacity = (int) Math.min(MAX_TILES, Math.max(0, (diskMiB * 1048576L - 12) / (RECORD_BYTES + 4)));
        int count = Math.min(tiles.size(),capacity);
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(count);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(RECORD_BYTES);
                DataOutputStream record = new DataOutputStream(buffer); CRC32 crc = new CRC32();
                for (int n = tiles.size()-count; n < tiles.size(); n++) {
                    buffer.reset(); var entry = tiles.get(n); TerrainTile tile = entry.tile();
                    record.writeLong(entry.key()); record.writeByte(tile.detail);
                    for (int i=0;i<64;i++) record.writeShort(tile.heightAt(i));
                    for (int i=0;i<64;i++) record.writeInt(tile.colorAt(i));
                    for (int i=0;i<64;i++) record.writeInt(tile.sideAt(i));
                    byte[] payload = buffer.toByteArray(); crc.reset(); crc.update(payload);
                    out.write(payload); out.writeInt((int)crc.getValue());
                }
            }
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
