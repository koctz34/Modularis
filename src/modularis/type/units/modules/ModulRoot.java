package modularis.type.units.modules;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;

import modularis.type.units.*;

/**
 * Root module - the brain of the machine. Exactly one is expected per unit.
 * It defines how many weapon / engine / ability modules the machine may hold.
 */
public class ModulRoot extends ModuleType{
    /** Slot counts this root grants. */
    public int weaponSlots = 2, engineSlots = 2, abilitySlots = 1;

    public ModulRoot(String name){
        super(name);
        category = ModuleCategory.root;
        weight = 3f;
        health = 200f;
        powerUse = 0.5f;
    }

    /** How many slots of the given pool this core contributes to the machine. */
    public int slotsProvided(SlotType type){
        return switch(type){
            case weapon -> weaponSlots;
            case engine -> engineSlots;
            case ability -> abilitySlots;
            default -> 0;
        };
    }

    @Override
    public void drawTop(Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        TextureRegion cell = cellRegion();
        if(cell.found()){
            Draw.color(teamColor(unit));
            Draw.rect(cell, x, y, w, h, rotation);
            Draw.color();
        }
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Weapon slots", "" + weaponSlots);
        stat(table, "Engine slots", "" + engineSlots);
        stat(table, "Ability slots", "" + abilitySlots);
    }
}
