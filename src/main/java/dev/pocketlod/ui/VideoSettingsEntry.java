package dev.pocketlod.ui;

import dev.pocketlod.LodEngine;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class VideoSettingsEntry {
    private VideoSettingsEntry() {}
    public static void register(LodEngine engine) {
        ScreenEvents.AFTER_INIT.register((client,screen,w,h) -> {
            if (screen instanceof VideoOptionsScreen) Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("PocketLOD..."),
                    b -> client.setScreen(new LodSettingsScreen(screen,engine))).dimensions(w-114,4,108,20).build());
        });
    }
}
