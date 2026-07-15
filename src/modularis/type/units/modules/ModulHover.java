package modularis.type.units.modules;

import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.util.*;

public class ModulHover extends ModulWheel{
    /** Hard weight limit for lift-off. The best hover on the machine sets it. */
    public float maxWeight = 100f;

    public float ringRadius = 9f;
    public float ringPhase = 90f;
    public float ringStroke = 2f;
    public float ringMinStroke = 0.12f;
    public int ringCircles = 2, ringSides = 6;
    public Color ringColor = Color.valueOf("64f1ff");

    public ModulHover(String name){
        super(name);
        category = ModuleCategory.wheel;
        weight = 1.5f;
        health = 80f;
        powerUse = 1.2f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Top speed", Strings.autoFixed(moveSpeed, 2));
        stat(table, "Haul capacity", Strings.autoFixed(haulWeight, 1) + " wt");
        stat(table, "Turn", Strings.autoFixed(rotateSpeed, 1));
        stat(table, "Max weight", "[cyan]" + Strings.autoFixed(maxWeight, 0) + "[]");
    }
}
