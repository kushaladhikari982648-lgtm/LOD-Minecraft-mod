package dev.pocketlod.render;

import dev.pocketlod.core.SurfaceMesher;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

public final class LodMesh implements AutoCloseable {
    private final VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private final BufferBuilder builder=new BufferBuilder(256);
    private int quads;
    public void upload(SurfaceMesher.Built built) {
        builder.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        for (int v=0;v<built.quads*4;v++) {
            int p=v*3,c=built.colors[v];
            builder.vertex(built.positions[p],built.positions[p+1],built.positions[p+2])
                    .color(c>>16&255,c>>8&255,c&255,255).next();
        }
        buffer.bind();
        try { buffer.upload(builder.end()); quads=built.quads; }
        finally { VertexBuffer.unbind(); }
    }
    public int quads() { return quads; }
    public void draw(Matrix4f model,Matrix4f projection,ShaderProgram shader) {
        if (quads==0) return;
        buffer.bind();
        try { buffer.draw(model,projection,shader); }
        finally { VertexBuffer.unbind(); }
    }
    @Override public void close() { buffer.close(); quads=0; }
}
