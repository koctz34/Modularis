package modularis.type.units.modules;

import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.world.*;

import modularis.type.units.*;

import static mindustry.Vars.*;

/** Throws the machine forward along its heading.*/
public class ModulDash extends ModulActive{
    public float distance = 96f;
    public float duration = 16f;
    public float minSpeedScale = 0.6f, maxSpeedScale = 1.6f;
    public Color color = Color.valueOf("4f9dff");

    /** The AI only dashes at a target this close to dead ahead. */
    public float aiAimCone = 25f;
    /** Below this share of its health the AI dashes away from a target behind it. */
    public float aiFleeHealth = 0.3f;

    public ModulDash(String name){
        super(name);
    }

    @Override
    public boolean canActivate(ModularUnitEntity unit){
        ModularPhysics.Stats s = unit.stats();
        return s != null && !s.immobile && unit.dashTime <= 0f;
    }

    @Override
    public void activate(ModularUnitEntity unit){
        unit.startDash(this);
    }

    /** A fast machine dashes further than a slow one, and whatever speed it already has carries over. */
    public float dashSpeed(ModularUnitEntity unit){
        ModularPhysics.Stats s = unit.stats();
        float top = s == null ? ModularPhysics.baseSpeed : s.speedMultiplier() * ModularPhysics.baseSpeed;
        float scale = Mathf.clamp(top / ModularPhysics.baseSpeed, minSpeedScale, maxSpeedScale);

        float forward = unit.vel.x * Mathf.cosDeg(unit.rotation) + unit.vel.y * Mathf.sinDeg(unit.rotation);
        return distance / Math.max(duration, 1f) * scale + Math.max(forward, 0f);
    }

    public float reach(ModularUnitEntity unit){
        return dashSpeed(unit) * duration;
    }

    @Override
    public boolean aiWantsToUse(ModularUnitEntity unit, Teamc target){
        float off = Angles.angleDist(unit.rotation, unit.angleTo(target));

        if(unit.healthf() < aiFleeHealth){
            return off > 180f - aiAimCone && clearAhead(unit);
        }

        float dst = unit.dst(target);
        float reach = unit.range();
        return off < aiAimCone && dst > reach * 0.8f && dst > reach(unit) && clearAhead(unit);
    }

    private boolean clearAhead(ModularUnitEntity unit){
        float length = reach(unit);
        float ex = unit.x + Angles.trnsx(unit.rotation, length);
        float ey = unit.y + Angles.trnsy(unit.rotation, length);
        Tile tile = world.tileWorld(ex, ey);
        return tile != null && !tile.solid() && (unit.hovering || !tile.floor().isDeep());
    }

    @Override
    public void buildStats(Table table){
        super.buildStats(table);
        stat(table, "Distance", Strings.autoFixed(distance * minSpeedScale / 8f, 1) + "-"
            + Strings.autoFixed(distance * maxSpeedScale / 8f, 1) + " tiles, by top speed");
    }
}
