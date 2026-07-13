package modularis.type.units;

import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;

import modularis.content.*;
import modularis.type.units.modules.*;

import static mindustry.Vars.*;

/**
 * Custom tank entity that carries its {@link ModularDesign} as real, serialized
 * state. Because the design lives on the entity (not in a side map), it survives
 * world save/load and is sent to clients in multiplayer.
 *
 * Registered with {@link mindustry.gen.EntityMapping} in the mod's init so the
 * network layer knows how to reconstruct it; {@link #classId()} returns that id.
 */
public class ModularUnitEntity extends TankUnit{
    /** Network/save class id, assigned at registration time. */
    public static int classID = -1;

    public ModularDesign design;

    public float originX, originY;

    /** How many modules have already been shed at damage thresholds. */
    public int shedCount;

    public float weaponRange = -1f;

    /** Item capacity summed from this machine's cargo modules. */
    public int cargoCapacity;

    public final Seq<PulsarMount> pulsars = new Seq<>();

    /** Sets the blueprint and derives dependent stats (max health, hitbox, weapon mounts). */
    public void setDesign(ModularDesign d){
        design = d == null ? null : d.copy();
        shedCount = 0;

        if(design != null && !design.isEmpty()){
            originX = design.centerX();
            originY = design.centerY();
        }
        rebuildMounts();

        if(design != null && !design.isEmpty()){
            //convertors can scale total health, so bake their multiplier straight into
            //maxHealth (rather than unit.healthMultiplier, which the engine also uses)
            float healthMult = ModularPhysics.compute(design).healthMultiplier;
            maxHealth(Math.max(1f, design.totalHealth() * healthMult));

            float w = Math.max(1, design.widthCells());
            float h = Math.max(1, design.heightCells());
            hitSize(Math.max(w, h) * ModularUnitType.cellWorld() * 0.8f);
            clampHealth();

            if(!isPlayer() && type != null){
                UnitController want = type.createController(this);
                if(want instanceof SuicideAI && !(controller() instanceof SuicideAI)){
                    controller(want);
                }
            }
        }
    }

    private void disposeMounts(){
        if(mounts == null) return;
        for(WeaponMount mount : mounts){
            if(mount == null) continue;

            if(mount.weapon != null && mount.weapon.continuous
                && mount.bullet != null && mount.bullet.owner == this){
                //let the beam wind down instead of popping out of existence
                mount.bullet.time = mount.bullet.lifetime - 10f;
                mount.bullet = null;
            }
            if(mount.sound != null) mount.sound.stop();
        }
    }

    private void rebuildMounts(){
        pulsars.clear();
        disposeMounts();
        mounts = new WeaponMount[0];
        abilities = new Ability[0];
        if(design == null) return;

        float cell = ModularUnitType.cellWorld();

        Seq<WeaponMount> weaponMounts = new Seq<>();
        Seq<Ability> abils = new Seq<>();

        for(PlacedModule m : design.modules){
            if(!design.isActive(m)) continue;

            if(m.type instanceof ModulTurret t){
                //offsets are baked against the FROZEN origin, so shedding modules
                //never drags the turrets out of place
                float lx = (m.x + t.w / 2f - originX) * cell;
                float ly = (m.y + t.h / 2f - originY) * cell;
                WeaponMount wm = t.createMount(lx, ly);
                if(wm != null){
                    wm.rotation = rotation;
                    weaponMounts.add(wm);
                }
            }else if(m.type instanceof ModulPulsar p){
                pulsars.add(new PulsarMount(m, p));

                Ability shield = p.createShield();
                if(shield != null) abils.add(shield);
            }
        }

        mounts = weaponMounts.toArray(WeaponMount.class);

        abilities = abils.toArray(Ability.class);
        for(Ability a : abilities){
            if(type != null) a.init(type);
            a.created(this);
        }

        recomputeDerived();
    }

    private void recomputeDerived(){
        float r = 0f;
        if(design != null){
            for(PlacedModule m : design.modules){
                if(!design.isActive(m)) continue;
                if(m.type instanceof ModulTurret t) r = Math.max(r, t.range());
            }
        }
        weaponRange = r;

        ModularPhysics.Stats s = design == null ? null : ModularPhysics.compute(design);

        armor(s == null ? 0f : s.armor);
        cargoCapacity = s == null ? 0 : s.cargoCapacity;
    }

    @Override
    public float range(){
        return weaponRange >= 0f ? weaponRange : super.range();
    }

    @Override
    public int itemCapacity(){
        return Math.max(cargoCapacity, 0);
    }

    // ---- battle damage: modules get torn off ----

    public void modulePos(PlacedModule m, Vec2 out){
        float cell = ModularUnitType.cellWorld();
        float mcx = m.x + m.type.w / 2f, mcy = m.y + m.type.h / 2f;
        out.set((mcx - originX) * cell, (mcy - originY) * cell).rotate(rotation - 90f).add(x, y);
    }

    public boolean canShed(PlacedModule m){
        return !(m.type instanceof ModulTurret) && m.type.category != ModuleCategory.root;
    }

    public void shedRandomModule(int seed){
        if(design == null) return;

        Seq<PlacedModule> options = design.modules.select(this::canShed);
        if(options.isEmpty()) return;

        Rand rand = new Rand(id() * 7919L + seed);
        removeModule(options.get(rand.random(options.size - 1)));
    }

    public void tearOffModule(){
        if(design == null || net.client()) return;

        Seq<PlacedModule> options = design.modules.select(this::canShed);
        if(options.isEmpty()) return;

        removeModule(options.random());
    }

    private void removeModule(PlacedModule victim){
        launchDebris(victim);
        design.modules.remove(victim);
        rebuildMounts();
    }

    public void launchDebris(PlacedModule m){
        modulePos(m, Tmp.v1);
        float sx = Tmp.v1.x, sy = Tmp.v1.y;

        float angle = Mathf.zero(Tmp.v1.dst(x, y), 0.01f)
            ? Mathf.random(360f)
            : Angles.angle(x, y, sx, sy) + Mathf.range(30f);
        float dist = Mathf.random(16f, 42f);
        float spin = Mathf.range(240f);

        MdlFX.moduleDebrisFly.at(sx, sy, angle, Color.white, new MdlFX.Debris(m.type, dist, spin));

        float lx = sx + Angles.trnsx(angle, dist);
        float ly = sy + Angles.trnsy(angle, dist);
        ModuleType type = m.type;
        Time.run(MdlFX.debrisFlyTime, () ->
            MdlFX.moduleDebrisRest.at(lx, ly, spin, Color.white, type));
    }

    private void explode(ModularPhysics.Stats s){
        if(!net.client()){
            Damage.damage(team, x, y, s.blastRadius, s.blastDamage);
        }

        Fx.dynamicExplosion.at(x, y, s.blastRadius / 8f);
        Effect.shake(Math.min(3f + s.c4Count * 2f, 14f), 18f, x, y);
        Sounds.explosionCrawler.at(x, y);
    }

    @Override
    public void destroy(){
        if(design != null){
            ModularPhysics.Stats s = ModularPhysics.compute(design);
            if(s.isKamikaze()) explode(s);
        }

        //the machine comes apart: about half of its modules are flung clear
        if(design != null && !design.modules.isEmpty()){
            Rand rand = new Rand(id() * 6151L);
            Seq<PlacedModule> left = design.modules.copy();
            int scatter = Math.max(1, left.size / 2);
            for(int i = 0; i < scatter && !left.isEmpty(); i++){
                launchDebris(left.remove(rand.random(left.size - 1)));
            }
        }
        super.destroy();
    }

    @Override
    public int classId(){
        return classID;
    }

    // ---- persistence (save files) ----

    @Override
    public void write(Writes write){
        super.write(write);
        write.str(design == null ? "" : design.serialize());
        write.i(shedCount);
    }

    @Override
    public void read(Reads read){
        super.read(read);
        setDesign(ModularDesign.read(read.str()));
        shedCount = read.i();
    }

    // ---- network sync (full state) ----

    @Override
    public void writeSync(Writes write){
        super.writeSync(write);
        write.str(design == null ? "" : design.serialize());
        write.i(shedCount);
    }

    @Override
    public void readSync(Reads read){
        super.readSync(read);
        setDesign(ModularDesign.read(read.str()));
        shedCount = read.i();
    }
}
