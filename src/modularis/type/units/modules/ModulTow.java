package modularis.type.units.modules;

import arc.scene.ui.layout.*;
import arc.util.*;

public class ModulTow extends ModuleType{
    public float breakForce = 320f;

    public ModulTow(String name){
        super(name);
        localizedName = "Tow Hitch";
        description = "Connects this machine to another tow hitch with a flexible cable. Click on the module, then select the nearest other module to connect them.";
        category = ModuleCategory.ability;
        w = h = 1;
        limit = 2;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Rope strength", Strings.autoFixed(breakForce, 0));
    }
}
