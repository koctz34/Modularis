package modularis.type.units.modules;

import arc.scene.ui.layout.*;
import arc.util.*;

/**
 * Base for every part that moves the machine - wheels, tracks, hovers and (later) rotors.
 */
public class ModulPropulsor extends ModuleType{
    /** Fallback thrust for parts that don't set one, as a fraction of {@link #haulWeight}. */
    public static final float thrustPerHaul = 0.75f;

    /** Which physics this part obeys, and how the machine ends up moving. */
    public PropulsionMode mode = PropulsionMode.ground;

    /** Top-speed rating. The machine's top speed is the capacity-weighted average of its parts. */
    public float moveSpeed = 0.6f;
    /** How much total machine weight this single part can bear at full speed. */
    public float haulWeight = 5f;
    public float rotateSpeed = 3f;
    public float grip = 0.9f;

    public float thrust = -1f;

    public ModulPropulsor(String name){
        super(name);
        category = ModuleCategory.wheel;
        weight = 1.5f;
        health = 80f;
        powerUse = 0.6f;
    }

    public float thrust(){
        return thrust >= 0f ? thrust : haulWeight * thrustPerHaul;
    }

    public float lift(){
        return 0f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Mode", "[#" + mode.color + "]" + mode.title + "[]");
        stat(table, "Top speed", Strings.autoFixed(moveSpeed, 2));
        stat(table, "Haul capacity", Strings.autoFixed(haulWeight, 1) + " wt");
        stat(table, "Thrust", Strings.autoFixed(thrust(), 1));
        stat(table, "Grip", Strings.autoFixed(grip, 2));
        stat(table, "Turn", Strings.autoFixed(rotateSpeed, 1));
    }
}
