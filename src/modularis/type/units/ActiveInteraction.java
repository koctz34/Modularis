package modularis.type.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class ActiveInteraction{
    public static final String packetName = "modularis-active";

    public static final KeyBind key = KeyBind.add("ability", KeyCode.shiftLeft, "modularis");

    public static float gear = 1f;

    private static boolean hudInstalled;

    public static void load(){
        if(!headless) key.load();
    }

    public static void update(){
        if(headless || !state.isGame() || state.isPaused()) return;
        if(Core.scene.hasField() || Core.scene.hasDialog() || ui.chatfrag.shown()) return;

        if(!mobile && Core.input.keyTap(key)){
            ModularUnitEntity unit = localUnit();
            if(unit == null) return;

            for(int i = 0; i < unit.actives.size; i++){
                if(use(unit, i)) break;
            }
        }
    }

    static @Nullable ModularUnitEntity localUnit(){
        return player != null && !player.dead() && player.unit() instanceof ModularUnitEntity e ? e : null;
    }

    static boolean use(ModularUnitEntity unit, int index){
        if(!unit.tryActivate(index)) return false;

        if(net.client()) Call.serverPacketReliable(packetName, Integer.toString(index));
        return true;
    }

    public static void handle(Player player, String data){
        if(player == null || !(player.unit() instanceof ModularUnitEntity unit)) return;
        try{
            unit.tryActivate(Integer.parseInt(data));
        }catch(NumberFormatException ignored){
        }
    }

    public static void installHud(){
        if(hudInstalled || ui.hudGroup == null) return;
        hudInstalled = true;

        ui.hudGroup.fill(t -> {
            t.touchable = Touchable.childrenOnly;
            t.bottom();
            t.visible(() -> ui.hudfrag.shown && !ui.minimapfrag.shown() && localUnit() != null);

            Table row = new Table();
            int[] shown = {0};
            row.update(() -> {
                ModularUnitEntity unit = localUnit();
                int count = unit == null ? 0 : unit.actives.size;
                if(count == shown[0]) return;

                shown[0] = count;
                row.clearChildren();
                for(int i = 0; i < count; i++){
                    row.add(new ActiveButton(i)).size(64f).pad(4f);
                }
            });
            t.add(row).padBottom(mobile ? 8f : 4f).row();

            if(!mobile){
                t.table(Tex.buttonTrans, bar -> {
                    bar.label(() -> "Speed " + Mathf.round(gear * 100f) + "%").width(92f).left().padRight(6f);
                    Slider slider = new Slider(0f, 1f, 0.05f, false);
                    slider.setValue(gear);
                    slider.moved(value -> gear = value);
                    bar.add(slider).width(130f);
                }).padBottom(6f);
            }
        });
    }

    private static class ActiveButton extends Element{
        final int index;

        ActiveButton(int index){
            this.index = index;
            touchable = Touchable.enabled;
            clicked(() -> {
                ModularUnitEntity unit = localUnit();
                if(unit != null) use(unit, index);
            });
        }

        @Override
        public void draw(){
            ModularUnitEntity unit = localUnit();
            if(unit == null || index >= unit.actives.size) return;

            ActiveMount mount = unit.actives.get(index);
            float w = getWidth(), h = getHeight();
            boolean ready = mount.charges > 0;

            Draw.color(Pal.darkestGray, 0.85f * parentAlpha);
            Fill.crect(x, y, w, h);

            TextureRegion region = mount.type.iconRegion();
            if(region != null && region.found()){
                float scale = Math.min(w * 0.8f / region.width, h * 0.8f / region.height);
                Draw.color(Color.white, (ready ? 1f : 0.35f) * parentAlpha);
                Draw.rect(region, x + w / 2f, y + h / 2f, region.width * scale, region.height * scale);
            }

            if(mount.charges < mount.maxCharges){
                Draw.color(mount.type.category.color, 0.4f * parentAlpha);
                Fill.crect(x, y, w, h * mount.progress);
            }

            Draw.color(ready ? mount.type.category.color : Color.gray, parentAlpha);
            Lines.stroke(Scl.scl(2f));
            Lines.rect(x, y, w, h);
            Draw.reset();

            if(mount.maxCharges > 1){
                Fonts.outline.draw(Integer.toString(mount.charges), x + w - Scl.scl(4f), y + h - Scl.scl(4f),
                    ready ? Color.white : Color.lightGray, 1f, false, Align.topRight);
            }
            if(!mobile && index == 0){
                Fonts.outline.draw(key.value.key.toString(), x + w / 2f, y + Scl.scl(14f),
                    Color.lightGray, 0.65f, false, Align.center);
            }
        }
    }
}
