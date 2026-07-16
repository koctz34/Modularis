package modularis.type.units;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;

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

    public static final float cellSize = spritePx / 4f;

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

        canDrown = true;
        shadowElevation = 0f;

        buildSpeed = 1f;
        drawBuildBeam = false;

        mineTier = 10;
        mineSpeed = 1f;
        mineFloor = true;
        mineWalls = false;
        drawCell = false;
        outlines = false;         //we draw our own composite outline
        generateFullIcon = false;
        engineSize = 0f;

        outlineColor = Color.valueOf("232527");
        outlineRadius = 3;
    }

    public static float cellWorld(){
        return cellSize;
    }

    public void applyMovementMode(ModularUnitEntity e){
        boolean hov = e.hovering;
        hovering = hov;
        omniMovement = hov;
        canDrown = !hov;
        shadowElevation = hov ? 0.1f : 0f;
        drag = hov ? 0.07f : 0.12f;
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

        updateShedding(e);

        ModularPhysics.Stats stats = ModularPhysics.compute(e.design);
        unit.speedMultiplier(stats.speedMultiplier());
        unit.damageMultiplier(stats.damageMultiplier);

        float dragMul = stats.dragMultiplier();
        if(e.hovering && e.isGrounded()){
            float floorDrag = e.floorOn().dragMultiplier;
            if(floorDrag > 0.0001f) dragMul /= floorDrag;
        }
        unit.dragMultiplier(dragMul);

        unit.disarmed(!stats.canShoot());
        unit.reloadMultiplier(Math.max(stats.fireRateMultiplier(), 0.05f));

        unit.buildSpeedMultiplier(stats.buildSpeed);

        mineSpeed = Math.max(0f, e.drillSpeed);
        if(e.drillRange > 0f){
            mineRange = e.drillRange + unit.hitSize / 2f;
        }

        if(e.weaponRangeMax > 0f){
            range = e.weaponRangeMin;
            maxRange = e.weaponRangeMax;
        }else{
            range = maxRange = mineRange;
        }

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

        for(DrillMount m : e.drills){
            worldPos(unit, m.placed, cell, cx, cy, rot, tmp);
            m.type.updateDrill(e, m, tmp.x, tmp.y);
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

    private void dustPos(Unit unit, PlacedModule m, float cell, float cx, float cy, float rot, Vec2 out){
        float mcx = m.x + m.type.w / 2f;
        float mcy = m.y + 0.5f;
        out.set((mcx - cx) * cell, (mcy - cy) * cell).rotate(rot).add(unit.x, unit.y);
    }

    private void emitWheelDust(Unit unit, ModularDesign design, float cell, float cx, float cy, float rot, float moveFrac){
        float chance = Mathf.clamp(moveFrac * 0.5f);
        for(PlacedModule m : design.modules){
            if(!(m.type instanceof ModulWheel) || m.type instanceof ModulHover) continue;
            if(!Mathf.chanceDelta(chance)) continue;
            dustPos(unit, m, cell, cx, cy, rot, tmp);
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

        if(unit instanceof ModularUnitEntity he && he.hovering){
            Draw.z(Layer.groundUnit - 0.02f);
            drawHoverRings(he, design, cell, cx, cy);
        }

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

        Draw.z(Layer.groundUnit + 0.03f);
        drawCargoItems(unit, cell, cx, cy);

        Draw.reset();
    }

    void drawCargoItems(Unit unit, float cell, float centerX, float centerY){
        if(!(unit instanceof ModularUnitEntity modular)) return;
        float rotation = unit.rotation - 90f;
        for(CargoMount cargo : modular.cargoMounts){
            cargo.updateVisual();
            Item item = cargo.visualItem;
            if(item == null || cargo.itemTime <= 0.01f) continue;
            worldPos(unit, cargo.placed, cell, centerX, centerY, rotation, tmp);
            float sin = Mathf.absin(Time.time, 5f, 1f);
            float size = (itemSize + sin) * cargo.itemTime;
            Draw.mixcol(Pal.accent, sin * 0.1f);
            Draw.rect(item.fullIcon, tmp.x, tmp.y, size, size, unit.rotation);
            Draw.mixcol();
            Draw.color(Pal.accent);
            float circleSize = ((3f + sin) * cargo.itemTime + 0.5f) * 2f;
            Draw.rect(itemCircleRegion, tmp.x, tmp.y, circleSize, circleSize);
            if(cargo.displayItem() != null && unit.isLocal() && !renderer.pixelate){
                Fonts.outline.draw(cargo.items.get(item) + "", tmp.x, tmp.y - 3f, Pal.accent,
                    0.25f * cargo.itemTime / Scl.scl(1f), false, Align.center);
            }
            Draw.reset();
        }
    }

    private void drawHoverRings(ModularUnitEntity e, ModularDesign design, float cell, float cx, float cy){
        float rot = e.rotation - 90f;
        for(PlacedModule m : design.modules){
            if(!(m.type instanceof ModulHover h) || !design.isActive(m)) continue;

            worldPos(e, m, cell, cx, cy, rot, tmp);
            Draw.color(h.ringColor);
            for(int c = 0; c < h.ringCircles; c++){
                float fin = (Time.time / h.ringPhase + (float)c / h.ringCircles) % 1f;
                Lines.stroke((1f - fin) * h.ringStroke + h.ringMinStroke);
                Lines.poly(tmp.x, tmp.y, h.ringSides, h.ringRadius * fin, e.rotation);
            }
        }
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
        boolean hover = unit instanceof ModularUnitEntity he && he.hovering;
        float off = hover ? 5f : 2f;

        Draw.z(Layer.groundUnit - 0.5f);
        Draw.color(0f, 0f, 0f, hover ? 0.16f : 0.22f);
        float rot = unit.rotation - 90f;
        for(PlacedModule m : design.modules){
            worldPos(unit, m, cell, cx, cy, rot, tmp);
            float dw = m.type.w * cell, dh = m.type.h * cell;
            Draw.rect(m.type.bodyRegion(), tmp.x + off, tmp.y - off, dw, dh, rot);
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

        if(design.color.equals(Color.white)) return;

        //whole machine rather than one per module.
        PaintRamp ramp = PaintRamp.of(design.color);
        for(int tone = 0; tone < 3; tone++){
            Draw.z(Layer.groundUnit + 0.002f * (tone + 1));
            Draw.color(ramp.tone(tone));

            for(PlacedModule m : design.modules){
                TextureRegion region = m.type.paintRegion(tone);
                if(region == null || !region.found()) continue;

                worldPos(unit, m, cell, cx, cy, rot, tmp);
                Draw.rect(region, tmp.x, tmp.y, m.type.w * cell, m.type.h * cell, rot);
            }
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

        if(!commands.contains(UnitCommand.mineCommand)){
            commands.add(UnitCommand.mineCommand);
        }
    }

    @Override
    public boolean allowCommand(Unit unit, UnitCommand command){
        if(command == UnitCommand.mineCommand
            && unit instanceof ModularUnitEntity e && !e.canMine()){
            return false;
        }
        return super.allowCommand(unit, command);
    }

    @Override
    public void load(){
        super.load();
        region = fullIcon = uiIcon = Core.atlas.find("modularis-modulare-tank-icon");
    }
}
