package modularis.type.units.modules;

import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;

/**
 * FUTURE!
 */
public class ModulRotor extends ModulPropulsor{
    public float lift = 40f;
    public float discRadius = 3f;
    public float counterTorque = 1f;

    public float spinSpeed = 24f;
    public Color bladeColor = Color.valueOf("ffd37f");

    public ModulRotor(String name){
        super(name);
        mode = PropulsionMode.air;

        grip = 0f;
        weight = 3f;
        health = 60f;
        powerUse = 4f;
    }

    @Override
    public float lift(){
        return lift;
    }

    public float discArea(){
        return Mathf.pi * discRadius * discRadius;
    }

    @Override
    public void buildStats(Table table){
        super.buildStats(table);
        stat(table, "Lift", "[#" + PropulsionMode.air.color + "]" + Strings.autoFixed(lift, 0) + " wt[]");
        stat(table, "Disc radius", Strings.autoFixed(discRadius, 1));
        stat(table, "Counter-torque", Strings.autoFixed(counterTorque, 2));
    }
}
