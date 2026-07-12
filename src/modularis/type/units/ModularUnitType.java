package modularis.type.units;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;

import modularis.content.*;
import modularis.type.units.modules.*;

/**
 * A ground (tank) unit whose body, stats, movement and mounted modules are driven
 * by a {@link ModularDesign}. The design lives on the {@link ModularUnitEntity}
 * itself, so it saves and syncs.
 *
 * Movement is physics-based ({@link ModularPhysics}); turrets and ability modules
 * are simulated per-tick from the design's mounts.
 */
public class ModularUnitType extends UnitType{
    /** Sprite pixels per grid cell (all module cells are 16x16). */
    public static final int spritePx = 16;

    private final Vec2 tmp = new Vec2();

    public ModularUnitType(String name){
        super(name);
        constructor = ModularUnitEntity::new;
        flying = false;
        hovering = false;
        omniMovement = false;
        rotateMoveFirst = false;
        faceTarget = false;

        speed = ModularPhysics.baseSpeed;
        rotateSpeed = 2.6f;
        accel = 0.1f;
        drag = 0.12f;
        health = 400f;
        hitSize = 18f;
        armor = 3f;

        drawCell = false;
        drawItems = false;
        outlines = false;         //we draw our own composite outline
        generateFullIcon = false;
        engineSize = 0f;

        outlineColor = Color.valueOf("232527");
        outlineRadius = 3;
    }

    public static float cellWorld(){
        TextureRegion r = MdlModules.basePanel.region();
        return spritePx * (r != null && r.found() ? r.scl() : 0.25f);
    }

    public static void assign(Unit unit, ModularDesign design){
        if(unit instanceof ModularUnitEntity e){
            e.setDesign(design);
            e.health(e.maxHealth());
        }
    }

    public static ModularDesign designFor(Unit unit){
        return unit instanceof ModularUnitEntity e ? e.design : null;
    }

    // ---- movement + module physics ----

    @Override
    public void update(Unit unit){
        super.update(unit);
        if(!(unit instanceof ModularUnitEntity e) || e.design == null) return;

        ModularPhysics.Stats stats = ModularPhysics.compute(e.design);
        unit.speedMultiplier(stats.speedMultiplier());
        unit.dragMultiplier(stats.dragMultiplier());

        float cell = cellWorld();
        float cx = e.design.centerX(), cy = e.design.centerY();
        float rot = unit.rotation - 90f;

        for(MenderMount m : e.menders){
            worldPos(unit, m.placed, cell, cx, cy, rot, tmp);
            m.type.updateMender(e, m, tmp.x, tmp.y);
        }

        //turbo heaters vent exhaust smoke (stateless, so just walk the design)
        for(PlacedModule m : e.design.modules){
            if(m.type instanceof ModulTurbo tb){
                worldPos(unit, m, cell, cx, cy, rot, tmp);
                tb.updateTurbo(e, m, tmp.x, tmp.y);
            }
        }

        //wheel dust while actually driving
        float moveFrac = unit.vel().len() / Math.max(0.001f, speed);
        if(moveFrac > 0.12f) emitWheelDust(unit, e.design, cell, cx, cy, rot, moveFrac);
    }

    /** Local (pre-rotation) offset of a module's centre from the unit centre. Writes into {@code out}. */
    private void localOffset(PlacedModule m, float cell, float cx, float cy, Vec2 out){
        float mcx = m.x + m.type.w / 2f, mcy = m.y + m.type.h / 2f;
        out.set((mcx - cx) * cell, (mcy - cy) * cell);
    }

    /** World position of a module's centre, given the unit's transform. Writes into {@code out}. */
    private void worldPos(Unit unit, PlacedModule m, float cell, float cx, float cy, float rot, Vec2 out){
        localOffset(m, cell, cx, cy, out);
        out.rotate(rot).add(unit.x, unit.y);
    }

    private void emitWheelDust(Unit unit, ModularDesign design, float cell, float cx, float cy, float rot, float moveFrac){
        float chance = Mathf.clamp(moveFrac * 0.5f);
        for(PlacedModule m : design.modules){
            if(!(m.type instanceof ModulWheel)) continue;
            if(!Mathf.chanceDelta(chance)) continue;
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            MdlFX.wheelDust.at(tmp.x, tmp.y, unit.rotation);
        }
    }

    // ---- drawing ----

    @Override
    public void draw(Unit unit){
        ModularDesign design = designFor(unit);
        if(design == null || design.isEmpty()){
            super.draw(unit);
            return;
        }

        float cell = cellWorld();
        float cx = design.centerX(), cy = design.centerY();

        drawShadow(unit, design, cell, cx, cy);

        //each pass gets its own z: the batch is z-sorted, so passes sharing a z can be
        //reordered - which let the dark outline cover the bodies (black silhouette).
        Draw.z(Layer.groundUnit - 0.01f);
        drawOutline(unit, design, cell, cx, cy);

        Draw.z(Layer.groundUnit);
        drawBodies(unit, design, cell, cx, cy);

        Draw.z(Layer.groundUnit + 0.01f);
        drawTops(unit, design, cell, cx, cy);

        Draw.z(Layer.groundUnit + 0.015f);
        drawTurretOutlines(unit);
        Draw.z(Layer.groundUnit + 0.02f);
        drawWeapons(unit);

        Draw.reset();
    }

    private void drawTurretOutlines(Unit unit){
        WeaponMount[] ms = unit.mounts;
        if(ms == null || ms.length == 0) return;

        float off = cellWorld() / spritePx * outlineRadius;
        Draw.color(outlineColor);
        for(WeaponMount m : ms){
            Weapon w = m.weapon;
            if(w == null) continue;
            TextureRegion reg = w.region;
            if(reg == null || !reg.found()) continue;

            float rot = unit.rotation - 90f;
            //matches Weapon.draw: when rotate, angle = (unit.rotation-90) + mount.rotation
            float weaponRot = rot + (w.rotate ? m.rotation : w.baseRotation);
            float recoilOffset = Mathf.pow(m.recoil, w.recoilPow) * w.recoil;
            float wx = unit.x + Angles.trnsx(rot, w.x, w.y) + Angles.trnsx(weaponRot, 0f, -recoilOffset);
            float wy = unit.y + Angles.trnsy(rot, w.x, w.y) + Angles.trnsy(weaponRot, 0f, -recoilOffset);

            for(int i = 0; i < 4; i++){
                Draw.rect(reg, wx + Angles.trnsx(i * 90f, off), wy + Angles.trnsy(i * 90f, off), weaponRot);
            }
        }
        Draw.color();
    }

    private void drawShadow(Unit unit, ModularDesign design, float cell, float cx, float cy){
        Draw.z(Layer.groundUnit - 0.5f);
        Draw.color(0f, 0f, 0f, 0.22f);
        float rot = unit.rotation - 90f;
        for(PlacedModule m : design.modules){
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            float dw = m.type.w * cell, dh = m.type.h * cell;
            Draw.rect(m.type.bodyRegion(), tmp.x + 2f, tmp.y - 2f, dw, dh, rot);
        }
        Draw.reset();
    }

    private void drawOutline(Unit unit, ModularDesign design, float cell, float cx, float cy){
        float rot = unit.rotation - 90f;
        float off = Math.max(0.5f, cell / spritePx * outlineRadius);
        Draw.color(outlineColor);
        for(PlacedModule m : design.modules){
            TextureRegion region = m.type.bodyRegion();
            if(region == null || !region.found()) continue;
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            float dw = m.type.w * cell, dh = m.type.h * cell;
            for(int i = 0; i < 4; i++){
                Draw.rect(region, tmp.x + Angles.trnsx(i * 90f, off), tmp.y + Angles.trnsy(i * 90f, off), dw, dh, rot);
            }
        }
        Draw.reset();
    }

    private void drawBodies(Unit unit, ModularDesign design, float cell, float cx, float cy){
        float rot = unit.rotation - 90f;
        for(PlacedModule m : design.modules){
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            float dw = m.type.w * cell, dh = m.type.h * cell;
            m.type.drawBody(unit, m, tmp.x, tmp.y, dw, dh, rot);
        }
        Draw.reset();
    }

    private void drawTops(Unit unit, ModularDesign design, float cell, float cx, float cy){
        float rot = unit.rotation - 90f;
        for(PlacedModule m : design.modules){
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            float dw = m.type.w * cell, dh = m.type.h * cell;
            m.type.drawTop(unit, m, tmp.x, tmp.y, dw, dh, rot);
        }
        Draw.reset();
    }

    @Override
    public void load(){
        super.load();
        //avoid the "missing texture" icon: borrow the command core sprite
        ModuleType root = MdlModules.root;
        if(root != null && root.region() != null && root.region().found()){
            region = fullIcon = uiIcon = root.region();
        }
    }
}
