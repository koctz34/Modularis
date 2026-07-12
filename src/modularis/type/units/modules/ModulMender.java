package modularis.type.units.modules;

import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.gen.*;

import modularis.content.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

/**
 * Ability module - a repair field emitter (like the Nova's heal). Periodically
 * pulses, healing friendly units within range.
 */
public class ModulMender extends ModuleType{
    /** Ticks between healing pulses. */
    public float reload = 120f;
    /** Heal radius in world units. */
    public float healRange = 90f;
    /** HP restored to each ally per pulse. */
    public float healAmount = 60f;
    /** Pulse colour. */
    public Color pulseColor = Color.valueOf("84f491");

    public ModulMender(String name){
        super(name);
        category = ModuleCategory.ability;
        slot = SlotType.ability;
        weight = 1.5f;
        health = 90f;
        powerUse = 0.7f;
    }

    public void updateMender(ModularUnitEntity unit, MenderMount mount, float x, float y){
        mount.charge += Time.delta;
        if(mount.charge < reload) return;
        mount.charge = 0f;

        if(!net.client()){
            Units.nearby(unit.team, x, y, healRange, other -> {
                if(other.damaged()) other.heal(healAmount);
            });
        }
        MdlFX.menderPulse.at(x, y, healRange, pulseColor);
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Heal", Strings.autoFixed(healAmount, 0) + " HP");
        stat(table, "Range", Strings.autoFixed(healRange / 8f, 1) + " tiles");
        stat(table, "Interval", Strings.autoFixed(reload / 60f, 1) + "s");
    }
}
