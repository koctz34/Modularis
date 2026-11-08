package modularis.type.units.modules;

import arc.scene.ui.layout.*;
import arc.util.*;

/** Only counts when this block makes the contact, with the target ahead. Extra rams widen the front, not the blow. */
public class ModulRam extends ModuleType{
    /** Ramming damage against what the same hull does without a ram. */
    public float damageMultiplier = 4f;
    /** Closing speed a blow counts as at most, in world units per tick. */
    public float topSpeed = 3.5f;
    /** Share of the usual recoil damage that makes it back into the hull. */
    public float selfDamageShare = 0.25f;

    public ModulRam(String name){
        super(name);
        category = ModuleCategory.weapon;
        weight = 9f;
        health = 400f;
        armor = 2f;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Ramming damage", "[scarlet]x" + Strings.autoFixed(damageMultiplier, 1) + "[]");
        stat(table, "Top impact speed", Strings.autoFixed(topSpeed * 60f / 8f, 1) + " tiles/s");
        stat(table, "Recoil damage", "[lime]x" + Strings.autoFixed(selfDamageShare, 2) + "[]");
        stat(table, "Works", "facing forward, on impact");
    }
}
