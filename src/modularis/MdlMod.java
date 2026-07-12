package modularis;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import modularis.content.*;
import modularis.type.units.*;

public class MdlMod extends Mod{

    public MdlMod(){
        Log.info("[stat][Modularis] [orange]Loaded constructor.");

        //register the custom modular unit entity so it can be saved and networked
        ModularUnitEntity.classID = EntityMapping.register("modularis-modular-unit", ModularUnitEntity::new);

        //listen for game load event
        Events.on(ClientLoadEvent.class, e -> {
            //show dialog upon startup
            Time.runTask(10f, () -> {
                BaseDialog dialog = new BaseDialog("frog");
                dialog.cont.add("behold").row();
                //mod sprites are prefixed with the mod name (this mod is called 'example-java-mod' in its config)
                dialog.cont.image(Core.atlas.find("modularis-frog")).pad(20f).row();
                dialog.cont.button("I see", dialog::hide).size(100f, 50f);
                dialog.show();
            });
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
