package modularis.type.units.modules;

import arc.scene.ui.layout.*;
import arc.util.*;

/**
 * Engine module - generates the power the rest of the machine runs on.
 */
public class ModulEngine extends ModuleType{
    public ModulEngine(String name){
        super(name);
        category = ModuleCategory.engine;
        slot = SlotType.engine;
        weight = 2f;
        health = 90f;
        powerProduction = 2f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Output", Strings.autoFixed(powerProduction * 60f, 1) + " power/s");
    }
}
