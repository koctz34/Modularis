package modularis;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.graphics.MultiPacker.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import modularis.content.*;
import modularis.type.units.*;
import modularis.type.units.modules.*;

import static mindustry.Vars.*;

public class MdlMod extends Mod{
    private static final int[][] bodyRamps = {
        {0x49180c, 0x753619, 0x975c2f},
        {0x4f4f52, 0x85858a, 0xd9d7e1}
    };

    public MdlMod(){
        Log.info("[stat][Modularis] [orange]Loaded constructor.");

        //register the custom modular unit entity so it can be saved and networked
        ModularUnitEntity.classID = EntityMapping.register("modularis-modular-unit", ModularUnitEntity::new);
        // Удалил от темплейта херь
        Events.run(Trigger.update, CargoInteraction::update);
        Events.run(Trigger.draw, CargoInteraction::drawOverlay);
        Events.run(Trigger.update, TowInteraction::update);
        Events.run(Trigger.draw, TowInteraction::draw);
        Events.on(ServerLoadEvent.class, event -> {
            netServer.addPacketHandler(CargoInteraction.packetName, CargoInteraction::handle);
            netServer.addPacketHandler(TowInteraction.packetName, TowInteraction::handle);
        });
        Events.on(ClientLoadEvent.class, event -> {
            CargoInteraction.installInput();
            TowInteraction.installInput();
            netServer.addPacketHandler(CargoInteraction.packetName, CargoInteraction::handle);
            netServer.addPacketHandler(TowInteraction.packetName, TowInteraction::handle);
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(player != null && player.unit() instanceof ModularUnitEntity e && e.type instanceof ModularUnitType mt){
                mt.omniMovement = e.hovering;

                mt.canBoost = e.boosting;
                mt.boostMultiplier = e.boostMultiplier;
            }
        });

    }

    @Override
    public void packSprites(MultiPacker packer){
        MdlModules.load();

        LoadedMod self = mods.getMod(MdlMod.class);
        if(self == null) return;

        ObjectMap<String, Fi> files = new ObjectMap<>();
        for(Fi f : self.root.child("sprites").findAll(f -> f.extension().equals("png"))){
            files.put(f.nameWithoutExtension(), f);
        }

        ObjectMap<String, Pixmap> pixmaps = new ObjectMap<>();
        for(ModuleType type : MdlModules.all){
            String sprite = type.bodySpriteName();
            if(pixmaps.containsKey(sprite)) continue;

            Fi file = files.get(sprite);
            if(file == null) continue;
            try{
                pixmaps.put(sprite, new Pixmap(file.readBytes()));
            }catch(Exception e){
                Log.err("[Modularis] Could not read '" + sprite + "' for paint generation.", e);
            }
        }

        for(ObjectMap.Entry<String, Pixmap> entry : pixmaps){
            Pixmap pix = entry.value;

            for(int tone = 0; tone < 3; tone++){
                Pixmap mask = new Pixmap(pix.width, pix.height);
                boolean any = false;

                for(int y = 0; y < pix.height; y++){
                    for(int x = 0; x < pix.width; x++){
                        int rgba = pix.get(x, y);

                        boolean on = (rgba & 0xff) >= 250 && bodyTone(rgba) >= tone;
                        any |= on;

                        mask.set(x, y, on ? 0xffffffff : 0xffffff00);
                    }
                }
                if(any){
                    packer.add(PageType.main, "modularis-" + entry.key + "-paint" + tone, mask);
                }
                mask.dispose();
            }
            pix.dispose();
        }
    }


    private static int bodyTone(int rgba){
        int rgb = rgba >>> 8;
        for(int[] ramp : bodyRamps){
            for(int i = 0; i < ramp.length; i++){
                if(ramp[i] == rgb) return i;
            }
        }
        return -1;
    }

    @Override
    public void loadContent(){
        Log.info("[stat][Modularis] [orange]Loading content.");
        MdlFX.load();
        MdlItems.load();
        MdlModules.load();
        MdlUnits.load();
        MdlBlocks.load();
    }

}
