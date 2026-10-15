package dev.pocketlod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.pocketlod.config.LodConfig;
import dev.pocketlod.render.LodRenderer;
import dev.pocketlod.ui.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class PocketLodClient implements ClientModInitializer {
    public static final Logger LOGGER=LoggerFactory.getLogger("PocketLOD");
    public static LodEngine engine;
    @Override public void onInitializeClient() {
        var loader=FabricLoader.getInstance(); var path=loader.getConfigDir().resolve("pocketlod.json");
        LodConfig config;
        try { config=LodConfig.load(path); }
        catch (java.io.IOException e) { LOGGER.warn("Using default settings",e); config=new LodConfig(); }
        engine=new LodEngine(config,path,loader.getGameDir().resolve("pocketlod-cache"));
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(new Identifier("pocketlod","terrain"),VertexFormats.POSITION_COLOR,shader -> LodRenderer.shader=shader));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(engine.renderer()::render);
        ClientChunkEvents.CHUNK_LOAD.register(engine::chunkLoaded);
        KeyBinding key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pocketlod.settings",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.pocketlod"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            engine.tick(client); while (key.wasPressed()) openMenu(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> engine.close());
        HudRenderCallback.EVENT.register((context,delta) -> {
            var client=MinecraftClient.getInstance();
            if (engine.config().showStats && client.world!=null && !client.options.hudHidden)
                context.drawTextWithShadow(client.textRenderer,Text.literal(engine.status()),4,4,0xD8E8FF);
        });
        VideoSettingsEntry.register(engine); commands();
        LOGGER.info("PocketLOD 0.4.0 initialized (Minecraft 1.20.1 / Java 17)");
    }
    private static void openMenu(MinecraftClient client) { client.setScreen(new LodSettingsScreen(client.currentScreen,engine)); }
    private static void commands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access) -> {
            var root=literal("pocketlod").executes(ctx -> { ctx.getSource().getClient().execute(() -> openMenu(ctx.getSource().getClient())); return 1; });
            root.then(literal("menu").executes(ctx -> { ctx.getSource().getClient().execute(() -> openMenu(ctx.getSource().getClient())); return 1; }));
            root.then(literal("status").executes(ctx -> { ctx.getSource().sendFeedback(Text.literal(engine.status())); return 1; }));
            root.then(literal("toggle").executes(ctx -> { engine.config().enabled=!engine.config().enabled; engine.changed(); ctx.getSource().sendFeedback(Text.literal(engine.status())); return 1; }));
            root.then(literal("distance").then(argument("chunks",IntegerArgumentType.integer(8,96)).executes(ctx -> {
                engine.config().lodRadiusChunks=IntegerArgumentType.getInteger(ctx,"chunks"); engine.changed(); ctx.getSource().sendFeedback(Text.literal(engine.status())); return 1;
            })));
            root.then(literal("genradius").then(argument("chunks",IntegerArgumentType.integer(6,64)).executes(ctx -> {
                engine.config().generationRadiusChunks=IntegerArgumentType.getInteger(ctx,"chunks"); engine.changed(); ctx.getSource().sendFeedback(Text.literal("Generation radius: "+engine.config().generationRadiusChunks+"c")); return 1;
            })));
            var quality=literal("quality"); String[] qualities={"low","medium","high","ultra"};
            for (int i=0;i<qualities.length;i++) { final int q=i; quality.then(literal(qualities[i]).executes(ctx -> {
                engine.config().graphicsQuality=q; engine.changed(); ctx.getSource().sendFeedback(Text.literal("Graphics: "+qualities[q])); return 1;
            })); }
            root.then(quality);
            var presets=literal("preset");
            for (String name:new String[]{"mobile4gb","lowendpc","ultra","tiny","balanced"}) presets.then(literal(name).executes(ctx -> {
                engine.config().preset(name); engine.changed(); ctx.getSource().sendFeedback(Text.literal("Preset: "+name)); return 1;
            }));
            root.then(presets);
            root.then(literal("generation").executes(ctx -> {
                var client=ctx.getSource().getClient(); client.execute(() -> LodSettingsScreen.confirmGeneration(client.currentScreen,engine)); return 1;
            }).then(literal("off").executes(ctx -> {
                engine.config().singleplayerGeneration=false; engine.changed(); ctx.getSource().sendFeedback(Text.literal("Singleplayer generation OFF")); return 1;
            })));
            root.then(literal("clear").executes(ctx -> {
                var client=ctx.getSource().getClient(); client.execute(() -> {
                    var parent=client.currentScreen;
                    client.setScreen(new ConfirmScreen(yes -> { if (yes) engine.clear(); client.setScreen(parent); },Text.literal("Clear PocketLOD terrain cache?"),Text.literal("Clears this server/world dimension only. Minecraft world chunks are not deleted.")));
                }); return 1;
            }).then(literal("confirm").executes(ctx -> { engine.clear(); ctx.getSource().sendFeedback(Text.literal("PocketLOD cache cleared")); return 1; })));
            dispatcher.register(root);
        });
    }
}
