package modularis.type.units.modules;

import arc.scene.ui.layout.*;
import arc.util.*;

/**
 * Wheel module - provides movement. A wheel has a top-speed rating and, more
 * importantly, a hauling capacity: it can carry a limited amount of weight at
 * full speed. Add more wheels to move a heavier machine; a machine that is too
 * heavy for its wheels becomes crawlingly slow. Wheels also draw power to spin.
 */
public class ModulWheel extends ModuleType{
    /** Top-speed rating this wheel allows (the best wheel sets the machine's top speed). */
    public float moveSpeed = 0.6f;
    /** How much total machine weight this single wheel can haul at full speed. */
    public float haulWeight = 5f;
    /** Turn-rate contribution (currently informational / averaged). */
    public float rotateSpeed = 3f;

    public ModulWheel(String name){
        super(name);
        category = ModuleCategory.wheel;
        weight = 1.5f;
        health = 80f;
        powerUse = 0.6f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Top speed", Strings.autoFixed(moveSpeed, 2));
        stat(table, "Haul capacity", Strings.autoFixed(haulWeight, 1) + " wt");
        stat(table, "Turn", Strings.autoFixed(rotateSpeed, 1));
    }
}
