package modularis.type.units.modules;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;

import modularis.type.units.*;

public abstract class ModulActive extends ModuleType{
    /** Ticks to refill one charge at full power. */
    public float cooldown = 60f * 6f;
    /** Sprite for the HUD button. Null = the module's own sprite. */
    public @Nullable String iconSprite;

    private TextureRegion iconRegion;

    public ModulActive(String name){
        super(name);
        category = ModuleCategory.active;
        slot = SlotType.ability;
        slotCost = 1;
        weight = 2f;
        health = 90f;
        powerUse = 1f;
    }

    public TextureRegion iconRegion(){
        if(iconSprite == null) return region();
        if(iconRegion == null && Core.atlas != null) iconRegion = Core.atlas.find("modularis-" + iconSprite);
        return iconRegion;
    }

    public boolean canActivate(ModularUnitEntity unit){
        return true;
    }

    public abstract void activate(ModularUnitEntity unit);

    /** Whether the AI should fire this right now, given the nearest enemy. */
    public boolean aiWantsToUse(ModularUnitEntity unit, Teamc target){
        return false;
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Cooldown", Strings.autoFixed(cooldown / 60f, 1) + "s");
        stat(table, "Charges", "+1 per module");
    }
}
