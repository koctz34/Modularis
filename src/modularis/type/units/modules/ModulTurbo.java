package modularis.type.units.modules;

import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;

import modularis.content.*;
import modularis.type.units.*;

/**
 * Ability module - a turbo heater. Multiplies the machine's top speed by
 * {@link #speedBoost}, at the cost of ability slots and a hefty power draw.
 * Vents exhaust smoke every so often while running.
 */
public class ModulTurbo extends ModuleType{
    /** Multiplies the machine's top speed (2 = twice as fast). */
    public float speedBoost = 2f;
    /** Chance per tick to puff out a cloud of exhaust smoke. */
    public float smokeChance = 0.1f;
    /** Colour of freshly vented smoke. */
    public Color smokeColor = Color.valueOf("9a8f86");

    public ModulTurbo(String name){
        super(name);
        category = ModuleCategory.ability;
        slot = SlotType.ability;
        slotCost = 2;
        weight = 3f;
        health = 130f;
        powerUse = 2.5f;
    }

    /** Per-tick venting. {@code x,y} is the module's world position. */
    public void updateTurbo(ModularUnitEntity unit, PlacedModule placed, float x, float y){
        if(Mathf.chanceDelta(smokeChance)){
            MdlFX.turboSmoke.at(x, y, unit.rotation, smokeColor);
        }
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Speed boost", "x" + Strings.autoFixed(speedBoost, 2));
    }
}
