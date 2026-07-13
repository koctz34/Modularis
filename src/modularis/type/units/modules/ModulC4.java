package modularis.type.units.modules;

import arc.audio.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;


public class ModulC4 extends ModuleType{
    /** Damage dealt by a single charge. */
    public float damage = 260f;
    /** Blast radius of a single charge, in world units. */
    public float radius = 48f;
    /** How far beyond the machine's own hitbox an enemy triggers detonation. */
    public float detonateRange = 14f;

    public Color blastColor = Color.valueOf("ffb24d");
    public Sound blastSound = Sounds.explosionCrawler;

    public ModulC4(String name){
        super(name);
        category = ModuleCategory.ability;
        slot = SlotType.ability;
        slotCost = 1;
        weight = 1.2f;
        health = 40f;
        powerUse = 0f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Blast damage", Strings.autoFixed(damage, 0));
        stat(table, "Blast radius", Strings.autoFixed(radius / 8f, 1) + " tiles");
        stat(table, "Behaviour", "[scarlet]Kamikaze[]");
    }
}
