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
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;

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

    /** Separates the serialized design from per-cargo inventory data. Must not appear in designs. */
    private static final char cargoDelim = '\u001f';

    public ModularDesign design;

    public float originX, originY;

    /** How many modules have already been shed at damage thresholds. */
    public int shedCount;
    public final int[] towUnitIds = {-1, -1}, towXs = new int[2], towYs = new int[2], towOtherXs = new int[2], towOtherYs = new int[2];
    public final float[] towLengths = new float[2], towTensions = new float[2];

    public float weaponRangeMin = -1f;
    public float weaponRangeMax = -1f;

    // ---- impact physics (see ModularImpact); all transient, it re-derives itself ----
    /** Hull spin from hits and recoil, in degrees per tick, on top of whatever the driver wants. */
    public transient float spin;
    /** Ticks left before this hull can hurt something by driving into it again. */
    public transient float ramCooldown;

    /** Round that has cleared the collision test but not yet applied its damage. */
    private transient @Nullable Bullet pendingBullet;
    private transient float pendingMultiplier = 1f, pendingTime = -1f;
    /** Where that round was, and where it was going, when the hulls met. */
    private transient float pendingHitX, pendingHitY, pendingAngle;
    private transient boolean pendingRicochet, pendingApplied;

    /** Scratch for the ray march through the design; Tmp is too easily trodden on here. */
    private final transient Vec2 rayVec = new Vec2();

    /** Per-mount shot counters, so we can spot the tick a gun actually fired. */
    private transient int[] lastShots = {};

    /** Item capacity summed from this machine's cargo modules. */
    public int cargoCapacity;

    /** True if any hover module is aboard. */
    public boolean hasHover;
    /** True when the machine actually floats (has a hover and is within its lift limit). */
    public boolean hovering;

    public PropulsionMode movementMode = PropulsionMode.ground;

    /** True if any booster module is aboard. */
    public boolean hasBooster;
    public boolean boosting;
    public float boostMultiplier = 1f;

    public @Nullable ModularPhysics.Stats stats;

    public ModularPhysics.Stats stats(){
        if(stats == null && design != null) stats = ModularPhysics.compute(design);
        return stats;
    }

    public final Seq<PulsarMount> pulsars = new Seq<>();
    public final Seq<DrillMount> drills = new Seq<>();
    public final Seq<CargoMount> cargoMounts = new Seq<>();

    /** Highest ore hardness this machine can cut. -1 = it carries no drill. */
    public int drillTier = -1;
    /** Summed drill speed. */
    public float drillSpeed;
    /** Longest drill reach, measured from the hull. */
    public float drillRange;

    /** Sets the blueprint and derives dependent stats (max health, hitbox, weapon mounts). */
    public void setDesign(ModularDesign d){
        applyDesign(d, true);
    }

    void applyDesignState(ModularDesign d, int shed){
        applyDesign(d, false);
        shedCount = shed;
    }

    private void applyDesign(ModularDesign d, boolean resetShed){
        design = d == null ? null : d.copy();
        if(resetShed) shedCount = 0;

        if(design != null && !design.isEmpty()){
            originX = design.centerX();
            originY = design.centerY();
        }
        rebuildMounts();

        if(design != null && !design.isEmpty()){
            //convertors can scale total health, so bake their multiplier straight into
            float healthMult = stats().healthMultiplier;
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
        ObjectMap<String, CargoMount> previousCargos = new ObjectMap<>();
        for(CargoMount cargo : cargoMounts) previousCargos.put(cargoKey(cargo.placed), cargo);
        pulsars.clear();
        drills.clear();
        cargoMounts.clear();
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
                if(wm != null) weaponMounts.add(wm);
            }else if(m.type instanceof ModulPulsar p){
                pulsars.add(new PulsarMount(m, p));

                Ability shield = p.createShield();
                if(shield != null) abils.add(shield);
            }else if(m.type instanceof ModulDrill d){
                drills.add(new DrillMount(m, d));
            }
            if(m.type.cargoCapacity > 0){
                CargoMount cargo = new CargoMount(m);
                CargoMount previous = previousCargos.get(cargoKey(m));
                if(previous != null){
                    cargo.items.set(previous.items);
                    cargo.lastItem = previous.lastItem;
                }
                cargoMounts.add(cargo);
            }
        }

        mounts = weaponMounts.toArray(WeaponMount.class);
        lastShots = new int[mounts.length];
        for(int i = 0; i < mounts.length; i++) lastShots[i] = mounts[i].totalShots;

        abilities = abils.toArray(Ability.class);
        for(Ability a : abilities){
            if(type != null) a.init(type);
            a.created(this);
        }

        recomputeDerived();
    }

    private void recomputeDerived(){
        float min = Float.MAX_VALUE, max = 0f;
        if(design != null){
            for(PlacedModule m : design.modules){
                if(!design.isActive(m)) continue;
                if(!(m.type instanceof ModulTurret t)) continue;

                float r = t.range();
                if(r <= 0f) continue;

                min = Math.min(min, r);
                max = Math.max(max, r);
            }
        }
        weaponRangeMin = min == Float.MAX_VALUE ? 0f : min;
        weaponRangeMax = max;

        ModularPhysics.Stats s = stats = design == null ? null : ModularPhysics.compute(design);

        armor(s == null ? 0f : s.armor);
        cargoCapacity = s == null ? 0 : s.cargoCapacity;
        drillTier = s == null ? -1 : s.drillTier;
        drillSpeed = s == null ? 0f : s.drillSpeed;
        drillRange = 1f;
        hasHover = s != null && s.hasHover;
        hovering = s != null && s.hovering();

        hasBooster = s != null && s.hasBooster;
        boosting = s != null && s.canBoost();
        boostMultiplier = s == null ? 1f : s.boostMultiplier;

        movementMode = s == null ? PropulsionMode.ground : s.mode();

        if(physref != null && physref.body != null) physref.body.mass = mass();
    }

    @Override
    public boolean canMine(){
        //super still applies the game rules (unitMineSpeed etc.)
        return drillTier >= 0 && drillSpeed > 0f && super.canMine();
    }

    @Override
    public boolean canMine(Item item){
        return item != null && drillTier >= item.hardness && canMine();
    }

    @Override
    public void update(){
        if(design != null && type instanceof ModularUnitType mt){
            mt.applyMovementMode(this);
        }
        super.update();
        updateImpactPhysics();
    }

    // ---- impact physics ----
    private void updateImpactPhysics(){
        ModularPhysics.Stats s = stats();
        if(s == null) return;

        updateRecoil(s);
        updateRamming(s);
        updateLateralGrip(s);

        if(Math.abs(spin) > ModularImpact.spinEpsilon){
            if(!net.client() || isLocal()){
                rotation += spin * Time.delta;
            }
            spin *= 1f - Math.min(s.spinDampRate() * Time.delta, 1f);
        }else{
            spin = 0f;
        }
    }

    private void updateLateralGrip(ModularPhysics.Stats s){
        if(movementMode != PropulsionMode.ground) return;

        float scrub = s.lateralScrubRate();
        if(scrub <= 0.001f) return;

        Vec2 v = vel();
        float fx = Mathf.cosDeg(rotation), fy = Mathf.sinDeg(rotation);
        float along = v.x * fx + v.y * fy;

        float sx = v.x - along * fx, sy = v.y - along * fy;
        float keep = 1f - Math.min(scrub * Time.delta, 1f);
        v.set(along * fx + sx * keep, along * fy + sy * keep);
    }

    public Vec2 comOffset(Vec2 out){
        ModularPhysics.Stats s = stats();
        if(s == null || design == null) return out.setZero();

        float cell = ModularUnitType.cellWorld();
        return out.set((s.centerX - originX) * cell, (s.centerY - originY) * cell).rotate(rotation - 90f);
    }

    private void updateRecoil(ModularPhysics.Stats s){
        if(mounts == null || mounts.length == 0) return;
        if(lastShots.length != mounts.length) lastShots = new int[mounts.length];

        comOffset(Tmp.v3);
        float topSpeed = type == null ? 1f : type.speed * Math.max(s.speedMultiplier(), 0.1f);

        for(int i = 0; i < mounts.length; i++){
            WeaponMount mount = mounts[i];
            if(mount == null || mount.weapon == null) continue;

            int fired = mount.totalShots - lastShots[i];
            lastShots[i] = mount.totalShots;
            if(fired <= 0 || mount.weapon.bullet == null) continue;

            Weapon w = mount.weapon;
            BulletType bt = w.bullet;
            float kick = ModularImpact.momentum(bt, bt.damage, bt.speed)
                * ModularImpact.recoilTransfer * Math.min(fired, 4);
            if(kick <= 0.01f) continue;

            //the barrel points along weaponRotation + 90, so the hull is shoved the other way
            float barrel = rotation + (w.rotate ? mount.rotation : w.baseRotation);
            Tmp.v1.trns(barrel + 180f, kick);
            ModularImpact.clampImpulse(Tmp.v1, mass(), topSpeed);
            impulse(Tmp.v1);

            Tmp.v2.set(w.x, w.y).rotate(rotation - 90f).sub(Tmp.v3);
            spin = Mathf.clamp(spin + ModularImpact.spinFrom(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, s.inertia),
                -ModularImpact.maxSpin, ModularImpact.maxSpin);
        }
    }

    private void updateRamming(ModularPhysics.Stats s){
        ramCooldown -= Time.delta;
        if(net.client() || ramCooldown > 0f || dead() || design == null) return;

        float speed = vel().len();
        if(speed < ModularImpact.ramMinSpeed) return;

        float reach = hitSize() / 2f;
        Units.nearbyEnemies(team, x, y, reach, other -> {
            if(other == this || other.dead() || ramCooldown > 0f) return;

            Tmp.v1.set(vel()).sub(other.vel());
            Tmp.v2.set(other.x - x, other.y - y);
            if(Tmp.v2.len() < 0.01f) return;

            float closing = Tmp.v1.dot(Tmp.v2.nor());
            float damage = ModularImpact.ramDamage(s.weight, closing);
            if(damage <= 1f) return;

            ramCooldown = ModularImpact.ramInterval;

            float hx = x + Tmp.v2.x * reach, hy = y + Tmp.v2.y * reach;
            other.damage(damage);
            damage(damage * ModularImpact.ramSelfDamage * Mathf.clamp(other.mass() / Math.max(mass(), 1f), 0f, 1f));

            Tmp.v3.set(Tmp.v2).scl(ModularImpact.momentumScale * s.weight * closing);
            other.impulse(Tmp.v3);
            impulse(Tmp.v3.scl(-0.5f));

            MdlFX.ramSparks.at(hx, hy, Tmp.v2.angle());
            Sounds.rockBreak.at(hx, hy, Mathf.random(0.7f, 1.1f));
            Effect.shake(Math.min(damage / 30f, 4f), 10f, hx, hy);
        });
    }

    @Override
    public float mass(){
        ModularPhysics.Stats s = stats();
        return s == null ? super.mass() : s.mass();
    }

    @Override
    public boolean collides(Hitboxc other){
        boolean result = super.collides(other);
        if(result && other instanceof Bullet b) prepareImpact(b);
        return result;
    }

    private void prepareImpact(Bullet b){
        ModularPhysics.Stats s = stats();
        if(s == null || b.type == null){
            pendingBullet = null;
            return;
        }

        float incidence = ModularImpact.incidence(x, y, b.x, b.y, b.rotation(),
            hitSize() * ModularImpact.hullRadiusScale);
        float multiplier = ModularImpact.deflectMultiplier(incidence, s.armor);
        boolean ricochet = b.type != MdlBullets.ricochet
            && Mathf.chance(ModularImpact.ricochetChance(incidence, s.armor));
        if(ricochet) multiplier = Math.min(multiplier, ModularImpact.ricochetDamage);

        pendingBullet = b;
        pendingTime = Time.time;
        pendingMultiplier = multiplier;
        pendingRicochet = ricochet;
        pendingApplied = false;
        pendingHitX = b.x;
        pendingHitY = b.y;
        pendingAngle = b.rotation();
    }

    @Override
    public void rawDamage(float amount){
        if(pendingBullet != null && !pendingApplied && Mathf.equal(pendingTime, Time.time)){
            pendingApplied = true;
            amount *= pendingMultiplier;
        }
        super.rawDamage(amount);
        rollShed(amount);
    }

    @Override
    public void collision(Hitboxc other, float cx, float cy){
        super.collision(other, cx, cy);
        if(other instanceof Bullet b) impact(b, cx, cy);
    }

    private void impact(Bullet b, float hx, float hy){
        ModularPhysics.Stats s = stats();
        if(s == null || b.type == null) return;

        boolean ricochet = pendingBullet == b && pendingRicochet;
        pendingBullet = null;

        float travel = b.rotation();
        float momentum = ModularImpact.momentum(b.type, b.damage, b.vel().len());
        float topSpeed = type == null ? 1f : type.speed * Math.max(s.speedMultiplier(), 0.1f);

        if(ricochet) momentum *= 0.35f;

        Tmp.v1.trns(travel, momentum);
        ModularImpact.clampImpulse(Tmp.v1, mass(), topSpeed);
        impulse(Tmp.v1);

        comOffset(Tmp.v2);
        float leverX = hx - (x + Tmp.v2.x), leverY = hy - (y + Tmp.v2.y);
        spin = Mathf.clamp(spin + ModularImpact.spinFrom(leverX, leverY, Tmp.v1.x, Tmp.v1.y, s.inertia),
            -ModularImpact.maxSpin, ModularImpact.maxSpin);

        if(ricochet){
            float out = reflect(travel, hx, hy);
            MdlFX.armorRicochet.at(hx, hy, out);
            spallOff(b, hx, hy, out);
            if(Mathf.chance(0.35f)) Sounds.shieldHit.at(hx, hy, Mathf.random(1.4f, 1.9f), 0.5f);
        }else{
            MdlFX.armorPenetrate.at(hx, hy, travel);
        }
    }

    private void spallOff(Bullet b, float hx, float hy, float angle){
        BulletType spall = MdlBullets.ricochet;
        if(spall == null) return;

        float out = angle + Mathf.range(ModularImpact.spallSpread);
        float sx = hx + Angles.trnsx(out, ModularImpact.spallOffset);
        float sy = hy + Angles.trnsy(out, ModularImpact.spallOffset);

        float share = b.damage * ModularImpact.spallDamage
            / Math.max(spall.damageMultiplier(b), 0.01f);

        spall.create(b.owner, b.team, sx, sy, out, Math.max(share, 1f), 1f, 1f, null);
    }

    private float reflect(float travel, float hx, float hy){
        float nx = hx - x, ny = hy - y;
        float len = Mathf.len(nx, ny);
        if(len < 0.001f) return travel;
        nx /= len;
        ny /= len;

        float dx = Mathf.cosDeg(travel), dy = Mathf.sinDeg(travel);
        float dot = dx * nx + dy * ny;
        return Mathf.angle(dx - 2f * dot * nx, dy - 2f * dot * ny);
    }

    @Override
    public float range(){
        return weaponRangeMax >= 0f ? weaponRangeMax : super.range();
    }

    @Override
    public int itemCapacity(){
        return Math.max(cargoCapacity, 0);
    }

    @Override
    public int maxAccepted(Item item){
        if(item == null) return 0;
        int accepted = 0;
        for(CargoMount cargo : cargoMounts){
            accepted += cargo.accept(item, Integer.MAX_VALUE);
        }
        return accepted;
    }

    @Override
    public boolean acceptsItem(Item item){
        return maxAccepted(item) > 0;
    }

    @Override
    public void addItem(Item item, int amount){
        if(item == null || amount <= 0) return;

        for(CargoMount cargo : cargoMounts){
            int accepted = cargo.accept(item, amount);
            if(accepted <= 0) continue;
            cargo.add(item, accepted);
            amount -= accepted;
            if(amount <= 0) return;
        }
    }

    @Override
    public void clearItem(){
        stack.amount = 0;
    }

    public int removeCargo(Item item, int amount){
        if(item == null || amount <= 0) return 0;
        int removed = 0;
        for(CargoMount cargo : cargoMounts){
            int taken = cargo.remove(item, amount - removed);
            removed += taken;
            if(removed >= amount) break;
        }
        return removed;
    }

    public Item cargoItem(){
        for(CargoMount cargo : cargoMounts){
            Item item = cargo.displayItem();
            if(item != null) return item;
        }
        return null;
    }

    public int cargoAmount(Item item){
        if(item == null) return 0;
        int amount = 0;
        for(CargoMount cargo : cargoMounts) amount += cargo.items.get(item);
        return amount;
    }

    String cargoKey(PlacedModule module){
        return module.type.name + ":" + module.x + ":" + module.y;
    }

    public CargoMount cargoAt(int x, int y){
        for(CargoMount cargo : cargoMounts){
            if(cargo.placed.x == x && cargo.placed.y == y) return cargo;
        }
        return null;
    }

    String stateData(){
        StringBuilder result = new StringBuilder(design == null ? "" : design.serialize());
        result.append(cargoDelim);
        boolean[] firstCargo = {true};
        for(CargoMount cargo : cargoMounts){
            if(!firstCargo[0]) result.append('|');
            firstCargo[0] = false;
            result.append(cargo.placed.x).append(',').append(cargo.placed.y).append(':');
            boolean[] firstItem = {true};
            cargo.items.each((item, amount) -> {
                if(!firstItem[0]) result.append(';');
                firstItem[0] = false;
                result.append(item.name).append('=').append(amount);
            });
        }
        return result.toString();
    }

    String designData(String stateData){
        int split = cargoSplitIndex(stateData);
        return split < 0 ? stateData : stateData.substring(0, split);
    }

    static int cargoSplitIndex(String stateData){
        if(stateData == null || stateData.isEmpty()) return -1;

        int delim = stateData.indexOf(cargoDelim);
        if(delim >= 0) return delim;

        if(stateData.charAt(0) == '#'){
            int last = stateData.lastIndexOf('#');
            return last > 0 ? last : -1;
        }
        return stateData.indexOf('#');
    }

    void readCargoData(String stateData){
        for(CargoMount cargo : cargoMounts){
            cargo.items.clear();
            cargo.lastItem = null;
        }
        int split = cargoSplitIndex(stateData);
        if(split < 0 || split + 1 >= stateData.length()) return;
        for(String entry : stateData.substring(split + 1).split("\\|")){
            String[] header = entry.split(":", 2);
            if(header.length != 2) continue;
            String[] coordinates = header[0].split(",", 2);
            if(coordinates.length != 2) continue;
            try{
                CargoMount cargo = cargoAt(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
                if(cargo == null || header[1].isEmpty()) continue;
                for(String stack : header[1].split(";")){
                    String[] itemData = stack.split("=", 2);
                    if(itemData.length != 2) continue;
                    Item item = content.item(itemData[0]);
                    if(item == null) continue;
                    int amount = Mathf.clamp(Integer.parseInt(itemData[1]), 0, cargo.capacity());
                    if(amount > 0) cargo.add(item, amount);
                }
            }catch(RuntimeException ignored){
            }
        }
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

    public Vec2 worldToCell(float wx, float wy, Vec2 out){
        float cell = ModularUnitType.cellWorld();
        return out.set(wx - x, wy - y).rotate(-(rotation - 90f)).scl(1f / cell).add(originX, originY);
    }

    public @Nullable PlacedModule moduleAlongRay(float wx, float wy, float travelAngle){
        if(design == null || design.isEmpty()) return null;

        worldToCell(wx, wy, rayVec);
        float px = rayVec.x, py = rayVec.y;

        float local = travelAngle - (rotation - 90f);
        float reach = Mathf.dst(px, py, originX, originY)
            + (design.widthCells() + design.heightCells()) * 0.5f + 2f;
        int steps = Math.min(Mathf.ceil(reach / ModularImpact.rayStep), ModularImpact.maxRaySteps);

        PlacedModule ahead = march(px, py, local, steps);
        if(ahead != null) return ahead;

        return march(px, py, local + 180f, steps);
    }

    private @Nullable PlacedModule march(float px, float py, float angle, int steps){
        float dx = Mathf.cosDeg(angle) * ModularImpact.rayStep;
        float dy = Mathf.sinDeg(angle) * ModularImpact.rayStep;

        for(int i = 0; i < steps; i++){
            PlacedModule hit = design.get(Mathf.floor(px), Mathf.floor(py));
            if(hit != null) return hit;
            px += dx;
            py += dy;
        }
        return null;
    }

    public @Nullable PlacedModule moduleNearest(float wx, float wy){
        if(design == null) return null;

        PlacedModule closest = null;
        float bestDst = Float.MAX_VALUE;
        for(PlacedModule m : design.modules){
            modulePos(m, Tmp.v5);
            float dst = Tmp.v5.dst2(wx, wy);
            if(dst < bestDst){
                bestDst = dst;
                closest = m;
            }
        }
        return closest;
    }

    private void rollShed(float damage){
        if(damage < ModularImpact.minShedDamage) return;
        if(net.client() || dead() || design == null || design.isEmpty()) return;

        PlacedModule target = shedTarget();
        if(target == null || !canShed(target)) return;
        if(!Mathf.chance(ModularImpact.shedChance(damage, target.type))) return;

        shedCount++;
        removeModule(target);
    }

    private @Nullable PlacedModule shedTarget(){
        if(pendingBullet != null && Mathf.equal(pendingTime, Time.time)){
            PlacedModule along = moduleAlongRay(pendingHitX, pendingHitY, pendingAngle);
            return along != null ? along : moduleNearest(pendingHitX, pendingHitY);
        }

        return design.modules.isEmpty() ? null : design.modules.random();
    }

    public void tearOffModule(){
        if(design == null || net.client()) return;

        Seq<PlacedModule> options = design.modules.select(this::canShed);
        if(options.isEmpty()) return;

        removeModule(options.random());
    }

    private void removeModule(PlacedModule victim){
        modulePos(victim, Tmp.v6);
        Sounds.explosionDull.at(Tmp.v6.x, Tmp.v6.y, Mathf.random(0.9f, 1.2f), 0.6f);

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
        write.str(stateData());
        write.i(shedCount);
        for(int i = 0; i < 2; i++){
            write.i(towUnitIds[i]); write.i(towXs[i]); write.i(towYs[i]);
            write.i(towOtherXs[i]); write.i(towOtherYs[i]); write.f(towLengths[i]); write.f(towTensions[i]);
        }
    }

    @Override
    public void read(Reads read){
        super.read(read);
        String stateData = read.str();
        applyDesignState(ModularDesign.read(designData(stateData)), read.i());
        readCargoData(stateData);
        for(int i = 0; i < 2; i++){
            towUnitIds[i] = read.i(); towXs[i] = read.i(); towYs[i] = read.i();
            towOtherXs[i] = read.i(); towOtherYs[i] = read.i(); towLengths[i] = read.f(); towTensions[i] = read.f();
        }
    }

    @Override
    public void afterSync(){
        if(design != null && !design.isEmpty()){
            if(mounts == null || mounts.length == 0) rebuildMounts();
            if(controller() != null) controller().unit(this);
            return;
        }
        super.afterSync();
    }

    @Override
    public void afterRead(){
        if(design != null && !design.isEmpty()){
            if(mounts == null || mounts.length == 0) rebuildMounts();
            if(controller() != null) controller().unit(this);
            return;
        }
        super.afterRead();
    }

    // ---- network sync (full state) ----

    @Override
    public void writeSync(Writes write){
        super.writeSync(write);
        write.str(stateData());
        write.i(shedCount);
        for(int i = 0; i < 2; i++){
            write.i(towUnitIds[i]); write.i(towXs[i]); write.i(towYs[i]);
            write.i(towOtherXs[i]); write.i(towOtherYs[i]); write.f(towLengths[i]); write.f(towTensions[i]);
        }
    }

    @Override
    public void readSync(Reads read){
        super.readSync(read);
        String incomingState = read.str();
        ModularDesign incoming = ModularDesign.read(designData(incomingState));
        int incomingShed = read.i();
        for(int i = 0; i < 2; i++){
            towUnitIds[i] = read.i(); towXs[i] = read.i(); towYs[i] = read.i();
            towOtherXs[i] = read.i(); towOtherYs[i] = read.i(); towLengths[i] = read.f(); towTensions[i] = read.f();
        }

        //rebuilding mounts resets WeaponMount state (rotation, reload, heat...). The design
        //string is sent every sync tick, but only changes when modules are shed - so skip
        //rebuild when nothing changed or client-side turrets spin forever.
        String cur = design == null ? "" : design.serialize();
        String next = incoming == null ? "" : incoming.serialize();
        if(!cur.equals(next) || shedCount != incomingShed){
            applyDesignState(incoming, incomingShed);
        }
        readCargoData(incomingState);
    }
}
