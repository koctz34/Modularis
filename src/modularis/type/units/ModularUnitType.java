package modularis.type.units;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;

import modularis.content.*;
import modularis.type.units.modules.*;

import static mindustry.Vars.*;

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

    public static final float[] shedThresholds = {0.7f, 0.5f, 0.2f};

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

        buildSpeed = 1f;
        drawBuildBeam = false;

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

        if(e.weaponRange >= 0f){
            range = maxRange = e.weaponRange;
        }

        updateShedding(e);

        ModularPhysics.Stats stats = ModularPhysics.compute(e.design);
        unit.speedMultiplier(stats.speedMultiplier());
        unit.dragMultiplier(stats.dragMultiplier());
        unit.damageMultiplier(stats.damageMultiplier);

        unit.disarmed(!stats.canShoot());
        unit.reloadMultiplier(Math.max(stats.fireRateMultiplier(), 0.05f));

        unit.buildSpeedMultiplier(stats.buildSpeed);

        if(stats.isKamikaze() && !net.client()){
            float reach = unit.hitSize / 2f + stats.detonateRange;
            if(Units.closestTarget(unit.team, unit.x, unit.y, reach, u -> true, b -> true) != null){
                unit.kill();
                return;
            }
        }

        float cell = cellWorld();
        float cx = e.originX, cy = e.originY;
        float rot = unit.rotation - 90f;

        for(PulsarMount m : e.pulsars){
            worldPos(unit, m.placed, cell, cx, cy, rot, tmp);
            m.type.updatePulse(e, m, tmp.x, tmp.y);
        }

        //any module can idly puff out an effect (turbo exhaust, steam, sparks...) - it's just
        //a field on ModuleType, so content decides. Stateless, so we walk the design directly.
        for(PlacedModule m : e.design.modules){
            ModuleType t = m.type;
            if(t.ambientEffect == null || !e.design.isActive(m)) continue;
            if(!Mathf.chanceDelta(t.ambientChance)) continue;

            worldPos(unit, m, cell, cx, cy, rot, tmp);
            t.ambientEffect.at(tmp.x, tmp.y, unit.rotation, t.ambientColor);
        }

        //wheel dust while actually driving
        float moveFrac = unit.vel().len() / Math.max(0.001f, speed);
        if(moveFrac > 0.12f) emitWheelDust(unit, e.design, cell, cx, cy, rot, moveFrac);
    }

    private void updateShedding(ModularUnitEntity e){
        float max = e.maxHealth();
        if(max <= 0f || e.design == null) return;

        float frac = e.health() / max;
        int target = 0;
        for(float th : shedThresholds){
            if(frac <= th) target++;
        }

        while(e.shedCount < target){
            e.shedCount++;
            e.shedRandomModule(e.shedCount);
        }
    }

    private void localOffset(PlacedModule m, float cell, float cx, float cy, Vec2 out){
        float mcx = m.x + m.type.w / 2f, mcy = m.y + m.type.h / 2f;
        out.set((mcx - cx) * cell, (mcy - cy) * cell);
    }

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
        float cx = unit instanceof ModularUnitEntity e ? e.originX : design.centerX();
        float cy = unit instanceof ModularUnitEntity e ? e.originY : design.centerY();

        drawShadow(unit, design, cell, cx, cy);

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

    /**
     * The AI gates all shooting on {@code hasWeapons()}, which is just
     * {@code type.weapons.size > 0}. Our turrets are per-instance mounts, not entries in
     * the type's weapon list, so without this the AI thinks every modular machine is
     * unarmed and never sets {@code isShooting} - the turrets would only ever fire under
     * direct player control.
     */
    /**
     * The dummy weapon is never mounted (setDesign replaces {@code unit.mounts} with the
     * real turrets), so we must keep it out of the outline-generation pass too - otherwise
     * the sprite packer would bake an outline for a weapon that is never drawn.
     */
    @Override
    public void getRegionsToOutline(Seq<TextureRegion> out){
        //deliberately empty: this unit's outlines are composed at draw time
    }

    @Override
    public UnitController createController(Unit unit){
        //Only stand in for the AI controller - never for CommandAI. Vanilla picks
        //  (!playerControllable || (team.isAI() && !rtsAi)) ? aiController.get() : new CommandAI()
        //so a player-team unit must keep its CommandAI or it stops answering RTS orders.
        //That's exactly how a vanilla crawler stays commandable while still being a suicide AI
        //in enemy waves. Replacing the controller unconditionally is what broke RTS control.
        if(isKamikaze(unit) && usesAiController(unit)){
            return new SuicideAI();
        }
        return super.createController(unit);
    }

    /** True when vanilla would hand this unit an AI controller rather than a CommandAI. */
    public boolean usesAiController(Unit unit){
        return !playerControllable || (unit.team.isAI() && !unit.team.rules().rtsAi);
    }

    public static boolean isKamikaze(Unit unit){
        return unit instanceof ModularUnitEntity e && e.design != null
            && ModularPhysics.compute(e.design).isKamikaze();
    }

    @Override
    public void init(){
        if(weapons.isEmpty()){
            float r = 0f;
            for(ModuleType t : MdlModules.all){
                if(t instanceof ModulTurret mt) r = Math.max(r, mt.range());
            }
            float reach = r > 0f ? r : 150f;

            weapons.add(new Weapon(){{
                display = false;
                mirror = false;
                rotate = false;
                //inert on every axis, in case it is ever somehow mounted
                bullet = new BasicBulletType(1f, 0f){{
                    speed = 1f;
                    lifetime = reach;   //range = speed * lifetime
                    range = reach;
                    damage = 0f;
                    collides = false;
                    hitEffect = despawnEffect = Fx.none;
                }};
            }});
        }

        super.init();
    }

    @Override
    public void load(){
        super.load();
        region = fullIcon = uiIcon = Core.atlas.find("modularis-modulare-tank-icon");
    }
}
