package dev.pocketlod.ui;

import dev.pocketlod.LodEngine;
import dev.pocketlod.config.LodConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class LodSettingsScreen extends Screen {
    private static final String[] QUALITIES={"LOW","MEDIUM","HIGH","ULTRA"};
    private final Screen parent;
    private final LodEngine engine;
    private RadiusRow lodRow,genRow;
    public LodSettingsScreen(Screen parent,LodEngine engine) { super(Text.literal("PocketLOD 0.4.0")); this.parent=parent; this.engine=engine; }
    @Override protected void init() {
        LodConfig c=engine.config(); int w=Math.min(410,width-16),x=(width-w)/2,half=(w-4)/2;
        int y=Math.max(32,(height-200)/2);
        button(x,y,half,"LOD: "+on(c.enabled),b -> { c.enabled=!c.enabled; b.setMessage(Text.literal("LOD: "+on(c.enabled))); engine.changed(); });
        button(x+half+4,y,w-half-4,"Graphics: "+QUALITIES[c.graphicsQuality],b -> {
            c.graphicsQuality=(c.graphicsQuality+1)%4; b.setMessage(Text.literal("Graphics: "+QUALITIES[c.graphicsQuality])); engine.changed();
        });
        lodRow=new RadiusRow(x,y+26,w,8,96,"LOD",() -> c.lodRadiusChunks,n -> c.lodRadiusChunks=n);
        genRow=new RadiusRow(x,y+52,w,6,64,"Gen",() -> c.generationRadiusChunks,n -> c.generationRadiusChunks=n);
        String[] presets={"mobile4gb","lowendpc","ultra"},labels={"4GB Mobile (32c)","Low-End PC (48c)","Ultra (64c)"};
        int third=(w-8)/3;
        for (int i=0;i<3;i++) { final String preset=presets[i]; button(x+i*(third+4),y+78,third,labels[i],b -> {
            c.preset(preset); engine.changed(); clearAndInit();
        }); }
        button(x,y+104,half,"SP Generation: "+on(c.singleplayerGeneration),b -> {
            if (c.singleplayerGeneration) { c.singleplayerGeneration=false; engine.changed(); clearAndInit(); }
            else confirmGeneration(this,engine);
        });
        button(x+half+4,y+104,w-half-4,detail(c),b -> { c.generateFeatures=!c.generateFeatures; b.setMessage(Text.literal(detail(c))); engine.changed(); });
        button(x,y+130,third,"Hide caves: "+on(c.hideCaves),b -> { c.hideCaves=!c.hideCaves; b.setMessage(Text.literal("Hide caves: "+on(c.hideCaves))); engine.changed(); });
        button(x+third+4,y+130,third,"Stats: "+on(c.showStats),b -> { c.showStats=!c.showStats; b.setMessage(Text.literal("Stats: "+on(c.showStats))); engine.changed(); });
        button(x+2*(third+4),y+130,third,"Done",b -> close());
    }
    private static String on(boolean v) { return v?"ON":"OFF"; }
    private static String detail(LodConfig c) { return "Detail: "+(c.generateFeatures?"TREES+TERRAIN":"SURFACE FAST"); }
    private ButtonWidget button(int x,int y,int w,String label,ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),action).dimensions(x,y,w,20).build());
    }
    public static void confirmGeneration(Screen back,LodEngine engine) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(yes -> {
            if (yes) { engine.config().singleplayerGeneration=true; engine.changed(); }
            client.setScreen(back);
        },Text.literal("Enable singleplayer distant generation?"),
                Text.literal("Generates and saves real chunks in your world. Uses CPU, memory and disk; keep a backup. Multiplayer is capture-only.")));
    }
    @Override public void tick() { lodRow.field.tick(); genRow.field.tick(); }
    @Override public void close() { engine.changed(); client.setScreen(parent); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context); super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFFF);
        int y=Math.max(32,(height-200)/2)+158;
        context.drawCenteredTextWithShadow(textRenderer,Text.literal("Exact radii: LOD 8–96c / generation 6–64c"),width/2,y,0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,Text.literal("F8 or /pocketlod • Iris shader packs pause LOD"),width/2,y+13,0x888888);
    }
    private final class RadiusRow {
        final TextFieldWidget field;
        final RadiusSlider slider;
        final int min,max;
        final IntSupplier get;
        final IntConsumer set;
        boolean syncing;
        RadiusRow(int x,int y,int w,int min,int max,String label,IntSupplier get,IntConsumer set) {
            this.min=min; this.max=max; this.get=get; this.set=set;
            int sw=w-112;
            slider=addDrawableChild(new RadiusSlider(x,y,sw,label,this));
            button(x+sw+4,y,20,"-",b -> change(get.getAsInt()-1,true));
            field=new TextFieldWidget(textRenderer,x+sw+28,y,56,20,Text.literal(label+" chunk radius"));
            field.setMaxLength(3); field.setText(Integer.toString(get.getAsInt()));
            field.setChangedListener(text -> {
                if (syncing) return;
                try { int n=Integer.parseInt(text); boolean valid=n>=min && n<=max;
                    field.setEditableColor(valid?0xFFFFFF:0xFF7777); if (valid) change(n,false);
                } catch (NumberFormatException ignored) { field.setEditableColor(0xFF7777); }
            });
            addDrawableChild(field); button(x+sw+88,y,20,"+",b -> change(get.getAsInt()+1,true));
        }
        void change(int n,boolean updateText) {
            n=LodConfig.clamp(n,min,max); set.accept(n); syncing=true;
            slider.sync();
            if (updateText) { field.setText(Integer.toString(n)); field.setEditableColor(0xFFFFFF); }
            syncing=false; engine.changed();
        }
    }
    private static final class RadiusSlider extends SliderWidget {
        final String label; final RadiusRow row;
        RadiusSlider(int x,int y,int w,String label,RadiusRow row) {
            super(x,y,w,20,Text.empty(),(row.get.getAsInt()-row.min)/(double)(row.max-row.min));
            this.label=label; this.row=row; updateMessage();
        }
        void sync() { value=(row.get.getAsInt()-row.min)/(double)(row.max-row.min); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Text.literal(label+": "+row.get.getAsInt()+"c / "+row.get.getAsInt()*16+"m")); }
        @Override protected void applyValue() { if (!row.syncing) row.change(row.min+(int)Math.round(value*(row.max-row.min)),true); }
    }
}
