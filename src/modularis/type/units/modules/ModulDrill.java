package modularis.type.units.modules;

import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.type.*;
import mindustry.world.*;

import modularis.content.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

public class ModulDrill extends ModuleType{
    /** Highest ore hardness this drill can chew through. */
    public int tier = 2;
    /** Mining speed this drill contributes. Drills stack. */
    public float drillSpeed = 1f;
    /** Ticks of grinding needed per item, before hardness scaling. */
    public float drillTime = 45f;
    /**
     * Reach for the RTS mine order, measured from the machine's HULL (half its hitbox is added
     * on top). A big machine's centre can never get close to the ore it is parked on, so a
     * centre-measured reach left it stranded just short of the tile.
     */
    public float mineRange = 90f;
    /** Chance per tick to puff dust while grinding. */
    public float smokeChance = 0.25f;

    public ModulDrill(String name){
        super(name);
        category = ModuleCategory.ability;
        slot = SlotType.ability;
        slotCost = 1;
        weight = 3f;
        health = 150f;
        powerUse = 2f;
    }

    public void updateDrill(ModularUnitEntity unit, DrillMount mount, float x, float y){
        if(unit.mineTile() != null){
            mount.progress = 0f;
            return;
        }

        Tile tile = world.tileWorld(x, y);
        if(tile == null){
            mount.progress = 0f;
            return;
        }

        Item drop = tile.drop();
        if(drop == null || drop.hardness > tier || !unit.acceptsItem(drop)){
            mount.progress = 0f;
            return;
        }

        mount.progress += Time.delta * drillSpeed;

        float need = drillTime * (1f + drop.hardness);
        if(mount.progress >= need){
            mount.progress -= need;
            if(!net.client()) unit.addItem(drop);
            MdlFX.drillSmoke.at(x, y, 0f, drop.color);
        }else if(Mathf.chanceDelta(smokeChance)){
            MdlFX.drillSmoke.at(x, y, 0f, drop.color);
        }
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Ore tier", "" + tier);
        stat(table, "Drill speed", Strings.autoFixed(drillSpeed, 2));
        stat(table, "Mining", "[lime]RTS mine order[]");
    }
}
