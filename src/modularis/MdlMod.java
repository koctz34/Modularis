package modularis;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import modularis.content.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

public class MdlMod extends Mod{

    public MdlMod(){
        Log.info("[stat][Modularis] [orange]Loaded constructor.");

        //register the custom modular unit entity so it can be saved and networked
        ModularUnitEntity.classID = EntityMapping.register("modularis-modular-unit", ModularUnitEntity::new);
        // Удалил от темплейта херь
        Events.run(Trigger.update, CargoInteraction::update);
        Events.run(Trigger.draw, CargoInteraction::drawOverlay);
        Events.on(ServerLoadEvent.class, event -> netServer.addPacketHandler(CargoInteraction.packetName, CargoInteraction::handle));
        Events.on(ClientLoadEvent.class, event -> {
            CargoInteraction.installInput();
            netServer.addPacketHandler(CargoInteraction.packetName, CargoInteraction::handle);
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(player != null && player.unit() instanceof ModularUnitEntity e && e.type instanceof ModularUnitType mt){
                mt.omniMovement = e.hovering;
            }
        });

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
