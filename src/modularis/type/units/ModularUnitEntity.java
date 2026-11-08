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
import mindustry.graphics.*;
import mindustry.type.*;

import modularis.content.*;
import modularis.type.units.modules.*;
import modularis.type.units.nav.*;

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

    private static final float designFreshTicks = 90f;
    private static final float designWindowTicks = 60f;
    private static final int designRefreshWindows = 6;
    private transient float designChangedAt = -1000f;

    /** Separates the serialized design from per-cargo inventory data. Must not appear in designs. */
    private static final char cargoDelim = '\u001f';

    public ModularDesign design;

    public float originX, originY;

    /** Terrain move increments, in world units. Must stay under the thinnest terrain collider part. */
    private static final float moveStep = 2f;
    /** Same softening the engine's physics applies to unit pushes. */
    private static final float pushSoftness = 1.25f;
    private static final float pushAllowance = 60f;
    private static final float maxMachineReach = 120f;
    /** How close a hull counts as touching what it rams. */
    private static final float ramContactSlack = 2f;

    public transient @Nullable ModularCollider collider;
    private transient @Nullable ModularCollider ramCollider;
    private final transient Vec2 pushVec = new Vec2(), navVec = new Vec2(), driveVec = new Vec2();
    private transient boolean reversing;
    private transient @Nullable ModularNavigator navigator;

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
    /** The hardest terrain stop since the last update, if any. */
    private transient float crashSpeed, crashCooldown;
    private transient int crashTileX, crashTileY;
    private transient boolean crashByRam;
    /** Enemy id -> when this hull last touched it, to tell a new blow from an ongoing shove. */
    private final transient IntFloatMap ramContacts = new IntFloatMap();

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
    private transient float[] recoilScales = {};

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
    public final Seq<ActiveMount> actives = new Seq<>();

    // ---- dash; none of it is saved, a loaded machine just has its charges back ----
    public transient float dashTime;
    private transient float dashAngle, dashSpeed;
    private transient float allowedSpeed, allowedTime;
    public transient Color dashColor = Color.white;
    public final transient Trail dashTrail = new Trail(12);
    private transient int dashSerial;
    private transient float lastLocalUse = -1000f;
    private transient float aiActiveTimer;

    private static final float speedGrace = 45f;

    @Override
    public float speed(){
        float speed = super.speed();
        return allowedTime > 0f ? Math.max(speed, allowedSpeed) : speed;
    }

    public void allowSpeed(float speed, float ticks){
        allowedSpeed = allowedTime > 0f ? Math.max(allowedSpeed, speed) : speed;
        allowedTime = Math.max(allowedTime, ticks);
    }

    public static boolean owns(Unit unit){
        return unit.isPlayer() ? unit.isLocal() : !net.client();
    }

    public static void shove(Unit unit, float dvx, float dvy){
        if(owns(unit)) unit.vel.add(dvx, dvy);
        if(unit instanceof ModularUnitEntity e){
            e.allowSpeed(e.baseSpeed() + Mathf.len(dvx, dvy) * shoveAllowance, speedGrace);
        }
    }

    public static void shoveImpulse(Unit unit, Vec2 impulse){
        float mass = Math.max(unit.mass(), 1f);
        shove(unit, impulse.x / mass, impulse.y / mass);
    }

    float baseSpeed(){
        return super.speed();
    }

    /** Slack on the turn cap, so a legitimate turn at full rate is never clipped. */
    private static final float turnMargin = 1.1f;
    /** A shove keeps adding up while it lasts, so the allowance has to be generous. */
    private static final float shoveAllowance = 4f;
    private transient float turnAnchor;
    private transient boolean turnAnchored;

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
        rebuildCollider();
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
        designChangedAt = Time.time;
        ObjectMap<String, CargoMount> previousCargos = new ObjectMap<>();
        for(CargoMount cargo : cargoMounts) previousCargos.put(cargoKey(cargo.placed), cargo);
        Seq<ActiveMount> previousActives = actives.copy();
        WeaponMount[] previousMounts = mounts;
        actives.clear();
        pulsars.clear();
        drills.clear();
        cargoMounts.clear();
        disposeMounts();
        mounts = new WeaponMount[0];
        abilities = new Ability[0];
        if(design == null) return;

        float cell = ModularUnitType.cellWorld();

        Seq<WeaponMount> weaponMounts = new Seq<>();
        FloatSeq kicks = new FloatSeq();
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
                    weaponMounts.add(wm);
                    kicks.add(t.recoilScale);
                }
            }else if(m.type instanceof ModulHinge hinge){
                float lx = (m.x + hinge.w / 2f - originX) * cell;
                float ly = (m.y + hinge.h / 2f - originY) * cell;
                hinge.createMounts(m, lx, ly, weaponMounts);
                while(kicks.size < weaponMounts.size) kicks.add(1f);
            }else if(m.type instanceof ModulPulsar p){
                pulsars.add(new PulsarMount(m, p));

                Ability shield = p.createShield();
                if(shield != null) abils.add(shield);
            }else if(m.type instanceof ModulDrill d){
                drills.add(new DrillMount(m, d));
            }else if(m.type instanceof ModulActive a){
                ActiveMount mount = actives.find(o -> o.type == a);
                if(mount == null){
                    actives.add(new ActiveMount(a));
                }else{
                    mount.maxCharges++;
                    mount.charges++;
                }
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

        for(ActiveMount mount : actives){
            ActiveMount previous = previousActives.find(o -> o.type == mount.type);
            if(previous != null){
                mount.charges = Math.min(previous.charges, mount.maxCharges);
                mount.progress = previous.progress;
            }
        }

        mounts = weaponMounts.toArray(WeaponMount.class);

        if(previousMounts != null){
            for(WeaponMount mount : mounts){
                if(!(mount instanceof HarpoonMount harpoon)) continue;
                for(WeaponMount old : previousMounts){
                    if(old instanceof HarpoonMount before && old.weapon.x == mount.weapon.x && old.weapon.y == mount.weapon.y){
                        harpoon.takeFrom(before);
                        break;
                    }
                }
            }
        }

        lastShots = new int[mounts.length];
        for(int i = 0; i < mounts.length; i++) lastShots[i] = mounts[i].totalShots;
        recoilScales = kicks.toArray();

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
                float r = m.type instanceof ModulTurret t ? t.range() : m.type instanceof ModulHinge h ? h.range(m) : 0f;
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
        limitTurn();
        updateActives();
        super.update();
        updateImpactPhysics();
        updateUnitCollisions();
        if(!net.client() || isLocal()) blockTurnIntoTerrain();
        turnAnchor = rotation;
    }

    // ---- collider ----
    private void rebuildCollider(){
        collider = design == null || design.isEmpty() ? null
            : ModularCollider.of(design, originX, originY, ModularUnitType.cellWorld());

        ramCollider = null;
        if(design == null) return;

        float cell = ModularUnitType.cellWorld();
        FloatSeq parts = new FloatSeq();
        for(PlacedModule m : design.modules){
            if(!(m.type instanceof ModulRam)) continue;
            parts.add((m.x + m.type.w / 2f - originX) * cell, (m.y + m.type.h / 2f - originY) * cell);
            parts.add(m.type.w * cell / 2f, m.type.h * cell / 2f);
        }
        if(!parts.isEmpty()) ramCollider = new ModularCollider(parts.toArray());
    }

    @Override
    public void hitbox(Rect rect){
        if(collider == null){
            super.hitbox(rect);
        }else{
            collider.bounds(x, y, rotation, 1f, rect);
        }
    }

    @Override
    public void hitboxTile(Rect rect){
        if(collider == null){
            super.hitboxTile(rect);
        }else{
            collider.bounds(x, y, rotation, ModularCollider.terrainScale, rect);
        }
    }

    @Override
    public boolean canPassOn(){
        return collider != null || super.canPassOn();
    }

    @Override
    public void move(float cx, float cy){
        EntityCollisions.SolidPred solid = solidity();
        if(collider == null || solid == null){
            super.move(cx, cy);
            return;
        }
        if(cx != cx || cy != cy) return;

        //already wedged (a wall was finished around it, say): let it drive out rather than trap it
        boolean free = collider.overlapsTiles(x, y, rotation, solid);
        moveAxis(cx, true, solid, free);
        moveAxis(cy, false, solid, free);
    }

    private void moveAxis(float amount, boolean horizontal, EntityCollisions.SolidPred solid, boolean free){
        while(Math.abs(amount) > 0.0001f){
            float step = Math.min(Math.abs(amount), moveStep) * Math.signum(amount);
            float px = x, py = y;

            if(horizontal){
                x += step;
            }else{
                y += step;
            }

            if(!free && collider.overlapsTiles(x, y, rotation, solid)){
                noteCrash(horizontal ? vel.x : vel.y, amount, solid);
                x = px;
                y = py;
                return;
            }
            amount -= step;
        }
    }

    private void noteCrash(float velocity, float amount, EntityCollisions.SolidPred solid){
        if(net.client() || velocity * amount <= 0f || Math.abs(velocity) <= crashSpeed) return;

        crashSpeed = Math.abs(velocity);
        crashTileX = collider.hitX;
        crashTileY = collider.hitY;
        crashByRam = ramCollider != null && Angles.angleDist(rotation, vel.angle()) <= ModularImpact.ramBlockCone
            && ramCollider.overlapsTiles(x, y, rotation, solid);
    }

    private void updateCrash(ModularPhysics.Stats s){
        crashCooldown -= Time.delta;

        float speed = crashSpeed;
        crashSpeed = 0f;
        if(speed < ModularImpact.crashMinSpeed || crashCooldown > 0f || dead()) return;

        crashCooldown = ModularImpact.crashInterval;

        boolean ram = crashByRam && s.hasRam;
        float base = ModularImpact.ramDamage(s.weight, Math.min(speed, ram ? s.ramTopSpeed : ModularImpact.dashRamSpeed));
        if(base <= 1f) return;

        Building build = world.build(crashTileX, crashTileY);
        if(build != null && build.team != team){
            build.damage(team, base * (ram ? s.ramMultiplier : 1f));
        }
        damage(base * (build != null ? ModularImpact.crashBuildingSelf : 1f) * (ram ? s.ramSelfShare : 1f));

        float hx = crashTileX * tilesize, hy = crashTileY * tilesize;
        Call.effect(MdlFX.ramSparks, hx, hy, Angles.angle(hx, hy, x, y), Color.white);
        Call.soundAt(Sounds.rockBreak, hx, hy, 1f, Mathf.random(0.7f, 1.1f));
        Effect.shake(Math.min(base / 30f, 4f), 10f, hx, hy);
    }

    private void blockTurnIntoTerrain(){
        if(collider == null || rotation == turnAnchor) return;

        EntityCollisions.SolidPred solid = solidity();
        if(solid == null) return;

        if(collider.overlapsTiles(x, y, rotation, solid) && !collider.overlapsTiles(x, y, turnAnchor, solid)){
            rotation = turnAnchor;
            spin = 0f;
        }
    }

    // ---- pathing ----
    @Override
    public void movePref(Vec2 vec){
        if(net.client() || isPlayer() || collider == null || !isGrounded() || vec.isZero()){
            drive(vec);
            return;
        }

        if(navigator == null) navigator = new ModularNavigator();
        navVec.set(vec);
        if(navigator.steer(this, navVec) == ModularNavigator.Result.reverse){
            moveAt(navVec);
        }else{
            drive(navVec);
        }
    }

    /** Replaces the engine's rotateMove, which can only thrust forwards. */
    private void drive(Vec2 vec){
        float gear = isLocal() && !mobile ? ActiveInteraction.gear : 1f;

        if(type == null || type.omniMovement || !isGrounded()){
            driveVec.set(vec).scl(gear);
            super.movePref(driveVec);
            return;
        }

        if(vec.isZero()){
            reversing = false;
            super.movePref(vec);
            return;
        }

        float target = vec.angle(), len = vec.len() * gear;
        float forward = vel.x * Mathf.cosDeg(rotation) + vel.y * Mathf.sinDeg(rotation);
        TrackDrive.Mode mode = TrackDrive.decide(rotation, target, forward, speed(), reversing, isPlayer());
        reversing = mode == TrackDrive.Mode.reverse;

        if(reversing){
            moveAt(driveVec.trns(rotation + 180f, len * TrackDrive.reverseSpeed));
            target += 180f;
        }else if(mode == TrackDrive.Mode.arc){
            moveAt(driveVec.trns(rotation, len));
        }

        rotation = Angles.moveToward(rotation, target, type.rotateSpeed * Time.delta * speedMultiplier());
    }

    public int navNeed(){
        float width = Math.min(design.widthCells(), design.heightCells()) * ModularUnitType.cellWorld() * ModularCollider.terrainScale;
        return Math.max(Mathf.ceil((width / 2f + tilesize / 2f) / tilesize), 1);
    }

    public int navTurnNeed(){
        return Math.max(Mathf.ceil((collider.radius * ModularCollider.terrainScale + tilesize / 2f) / tilesize), navNeed());
    }

    private boolean rammedBy(Unit other){
        return ramCollider != null && Angles.angleDist(rotation, angleTo(other)) <= ModularImpact.ramBlockCone
            && touches(ramCollider, other);
    }

    private boolean touches(ModularCollider shape, Unit other){
        if(other instanceof ModularUnitEntity machine && machine.collider != null){
            //nudge it onto us, since pushes keep real contact from quite lasting
            float dx = x - other.x, dy = y - other.y;
            float len = Math.max(Mathf.len(dx, dy), 0.01f);
            return shape.pushFrom(x, y, rotation, machine.collider,
                other.x + dx / len * ramContactSlack, other.y + dy / len * ramContactSlack, other.rotation, pushVec);
        }
        return shape.pushFromCircle(x, y, rotation, other.x, other.y, other.hitSize() / 2f + ramContactSlack, pushVec);
    }

    private void updateUnitCollisions(){
        if(collider == null || dead() || (net.client() && !isLocal())) return;

        collider.bounds(x, y, rotation, 1f, Tmp.r3);
        int layer = collisionLayer();
        Units.nearby(Tmp.r3, other -> {
            if(other == this || other.dead() || other.collisionLayer() != layer) return;

            boolean machine = other instanceof ModularUnitEntity e && e.collider != null;
            boolean hit;
            if(machine){
                ModularUnitEntity e = (ModularUnitEntity)other;
                hit = collider.pushFrom(x, y, rotation, e.collider, e.x, e.y, e.rotation, pushVec);
            }else{
                if(other.type == null || !other.type.physics) return;
                hit = collider.pushFromCircle(x, y, rotation, other.x, other.y, other.hitSize() / 2f, pushVec);
            }
            if(!hit) return;

            float own = mass(), theirs = other.mass();
            float share = theirs / Math.max(own + theirs, 1f);
            float px = pushVec.x * share / pushSoftness, py = pushVec.y * share / pushSoftness;
            if(owns(this)){
                move(px, py);
            }
            allowSpeed(baseSpeed() + Mathf.len(px, py) * pushAllowance, speedGrace);

            if(!machine && owns(other)){
                other.move(-pushVec.x * (1f - share) / pushSoftness, -pushVec.y * (1f - share) / pushSoftness);
            }
        });
    }

    public static void pushLocalUnit(){
        if(!net.client() || player == null || player.dead()) return;

        Unit unit = player.unit();
        if(unit instanceof ModularUnitEntity || unit.type == null || !unit.type.physics) return;

        float r = unit.hitSize() / 2f;
        int layer = unit.collisionLayer();
        Units.nearby(unit.x - r - maxMachineReach, unit.y - r - maxMachineReach, (r + maxMachineReach) * 2f, (r + maxMachineReach) * 2f, other -> {
            if(!(other instanceof ModularUnitEntity machine) || machine.collider == null || machine.dead()
                || machine.collisionLayer() != layer) return;
            if(!machine.collider.pushFromCircle(machine.x, machine.y, machine.rotation, unit.x, unit.y, r, machine.pushVec)) return;

            float share = machine.mass() / Math.max(machine.mass() + unit.mass(), 1f);
            unit.move(-machine.pushVec.x * share / pushSoftness, -machine.pushVec.y * share / pushSoftness);
        });
    }

    public void limitTurn(){
        if(net.client() && !isLocal()) return;

        ModularPhysics.Stats s = stats();
        if(s == null) return;

        if(!turnAnchored){
            turnAnchored = true;
            turnAnchor = rotation;
            return;
        }

        rotation = Angles.moveToward(turnAnchor, rotation, s.turnRate * turnMargin * Time.delta);
        blockTurnIntoTerrain();
        turnAnchor = rotation;
    }

    // ---- active modules ----
    private void updateActives(){
        ModularPhysics.Stats s = stats();
        if(s != null){
            for(ActiveMount mount : actives) mount.update(s.powerRatio);
        }

        allowedTime = Math.max(allowedTime - Time.delta, 0f);
        if(dashTime > 0f){
            if(owns(this)) vel().trns(dashAngle, dashSpeed);

            dashTime -= Time.delta;
            if(dashTime <= 0f && owns(this) && s != null && type != null){
                //drop back to what the drivetrain can hold, or it would coast for ages
                vel().limit(type.speed * Math.max(s.speedMultiplier(), 0.1f));
            }
        }

        if(!headless){
            if(dashTime > 0f){
                dashTrail.update(x, y);
            }else{
                dashTrail.shorten();
            }
        }

        updateActiveAi();
    }

    private void updateActiveAi(){
        if(net.client() || actives.isEmpty() || isPlayer() || dead()) return;
        if(controller() instanceof CommandAI ai && ai.hasStance(MdlStances.holdActives)) return;

        aiActiveTimer -= Time.delta;
        if(aiActiveTimer > 0f) return;
        aiActiveTimer = 15f;

        float reach = Math.max(range(), 8f) + 120f;
        Teamc target = Units.closestTarget(team, x, y, reach, u -> true, b -> true);
        if(target == null) return;

        for(int i = 0; i < actives.size; i++){
            ActiveMount mount = actives.get(i);
            if(mount.charges > 0 && mount.type.aiWantsToUse(this, target) && tryActivate(i)) return;
        }
    }

    public boolean tryActivate(int index){
        if(index < 0 || index >= actives.size || dead()) return false;

        ActiveMount mount = actives.get(index);
        if(mount.charges <= 0 || !mount.type.canActivate(this)) return false;

        mount.charges--;
        if(net.client()) lastLocalUse = Time.time;
        mount.type.activate(this);
        return true;
    }

    public void startDash(ModulDash dash){
        dashSerial++;
        beginDash(dash);
    }

    private void beginDash(ModulDash dash){
        dashTime = dash.duration;
        dashAngle = rotation;
        dashSpeed = dash.dashSpeed(this);
        allowSpeed(dashSpeed, dash.duration + speedGrace);
        dashColor = dash.color;

        MdlFX.dashBurst.at(x, y, rotation, dash.color);
        Sounds.shootMissileSmall.at(x, y, Mathf.random(0.6f, 0.75f), 0.8f);
    }

    private void writeActives(Writes write){
        write.b(actives.size);
        for(ActiveMount mount : actives){
            write.b(mount.charges);
            write.f(mount.progress);
        }
        write.i(dashSerial);
        HarpoonWeapon.writeSync(this, write);
    }

    private void readActives(Reads read){
        boolean predicting = isLocal() && Time.time - lastLocalUse < 45f;

        int count = read.b();
        for(int i = 0; i < count; i++){
            int charges = read.b();
            float progress = read.f();
            if(i < actives.size && !predicting){
                actives.get(i).charges = Math.min(charges, actives.get(i).maxCharges);
                actives.get(i).progress = progress;
            }
        }

        int serial = read.i();
        if(serial != dashSerial && !isLocal() && actives.any()){
            for(ActiveMount mount : actives){
                if(mount.type instanceof ModulDash dash){
                    beginDash(dash);
                    break;
                }
            }
        }
        dashSerial = serial;
        HarpoonWeapon.readSync(this, read);
    }

    // ---- impact physics ----
    private void updateImpactPhysics(){
        ModularPhysics.Stats s = stats();
        if(s == null) return;

        updateRecoil(s);
        updateRamming(s);
        updateCrash(s);
        updateLateralGrip(s);

        if(Math.abs(spin) > ModularImpact.spinEpsilon){
            if(owns(this)) rotation += spin * Time.delta;
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
                * ModularImpact.recoilTransfer * Math.min(fired, 4) * (i < recoilScales.length ? recoilScales[i] : 1f);
            if(kick <= 0.01f) continue;

            //the barrel points along weaponRotation + 90, so the hull is shoved the other way
            float barrel = rotation + (w.rotate ? mount.rotation : w.baseRotation);
            Tmp.v1.trns(barrel + 180f, kick);
            ModularImpact.clampImpulse(Tmp.v1, mass(), topSpeed);
            shoveImpulse(this, Tmp.v1);

            Tmp.v2.set(w.x, w.y).rotate(rotation - 90f).sub(Tmp.v3);
            spin = Mathf.clamp(spin + ModularImpact.spinFrom(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, s.inertia),
                -ModularImpact.maxSpin, ModularImpact.maxSpin);
        }
    }

    private void updateRamming(ModularPhysics.Stats s){
        ramCooldown -= Time.delta;
        if(dead() || design == null) return;

        float speed = vel().len();

        //every tick, even when nothing can land: contact has to be noticed to count as ongoing
        if(ramContacts.size > 32) ramContacts.clear();
        float reach = collider == null ? hitSize() / 2f : collider.radius;
        Units.nearbyEnemies(team, x, y, reach, other -> {
            if(other == this || other.dead()) return;
            boolean rammed = rammedBy(other);
            if(!rammed && collider != null && !touches(collider, other)) return;

            boolean fresh = Time.time - ramContacts.get(other.id, -ModularImpact.ramContactMemory - 1f) > ModularImpact.ramContactMemory;
            //a new target met during the cooldown keeps its first blow for when it's over
            if(ramCooldown > 0f){
                if(!fresh) ramContacts.put(other.id, Time.time);
                return;
            }
            ramContacts.put(other.id, Time.time);
            if(speed < ModularImpact.ramMinSpeed) return;

            Tmp.v1.set(vel()).sub(other.vel());
            Tmp.v2.set(other.x - x, other.y - y);
            if(Tmp.v2.len() < 0.01f) return;
            Tmp.v2.nor();

            float closing;
            if(rammed){
                //the blow goes where the plate is pointing, whatever part of the target it meets
                closing = Math.min(Tmp.v1.x * Mathf.cosDeg(rotation) + Tmp.v1.y * Mathf.sinDeg(rotation), s.ramTopSpeed);
            }else{
                closing = Tmp.v1.dot(Tmp.v2);
                if(dashTime > 0f) closing = Math.min(closing, ModularImpact.dashRamSpeed);
            }

            float share = fresh ? 1f : ModularImpact.ramSustainedShare;
            float damage = ModularImpact.ramDamage(s.weight, closing) * (rammed ? s.ramMultiplier : 1f) * share;
            if(damage <= 1f) return;

            ramCooldown = ModularImpact.ramInterval;

            float hx = x + Tmp.v2.x * reach, hy = y + Tmp.v2.y * reach;
            if(!net.client()){
                other.damage(damage);
                damage(damage / (rammed ? s.ramMultiplier : 1f) * (rammed ? s.ramSelfShare : 1f)
                    * ModularImpact.ramSelfDamage * Mathf.clamp(other.mass() / Math.max(mass(), 1f), 0f, 1f));
            }

            if(rammed && fresh){
                MdlFX.ramSparks.at(hx, hy, rotation + 25f);
                MdlFX.ramSparks.at(hx, hy, rotation - 25f);
                Sounds.explosionDull.at(hx, hy, Mathf.random(0.8f, 1f));
            }

            Tmp.v3.set(Tmp.v2).scl(ModularImpact.momentumScale * s.weight * closing * share);
            shoveImpulse(other, Tmp.v3);
            shoveImpulse(this, Tmp.v3.scl(-0.5f));

            MdlFX.ramSparks.at(hx, hy, Tmp.v2.angle());
            if(fresh){
                Sounds.rockBreak.at(hx, hy, Mathf.random(0.7f, 1.1f));
                Effect.shake(Math.min(damage / 30f, 4f), 10f, hx, hy);
            }
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
        if(result && other instanceof Bullet b){
            if(collider != null && !collider.segmentHits(x, y, rotation, b.lastX(), b.lastY(), b.x, b.y, b.hitSize() / 2f + deltaLen())){
                return false;
            }
            prepareImpact(b);
        }
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
        shoveImpulse(this, Tmp.v1);

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
        return (design == null ? "" : design.serialize()) + cargoData();
    }

    String cargoData(){
        StringBuilder result = new StringBuilder();
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
        if(!Mathf.chance(ModularImpact.shedChance(damage, target))) return;

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

        shedCount++;
        removeModule(options.random());
    }

    private void removeModule(PlacedModule victim){
        shedEffects(victim);
        design.modules.remove(victim);
        rebuildCollider();
        rebuildMounts();
    }

    private void shedEffects(PlacedModule victim){
        modulePos(victim, Tmp.v6);
        Sounds.explosionDull.at(Tmp.v6.x, Tmp.v6.y, Mathf.random(0.9f, 1.2f), 0.6f);

        if(victim.type instanceof ModulHinge && victim.cannon != null && !victim.cannon.isEmpty()){
            blowOffCannon(victim);
        }

        launchDebris(victim);
    }

    private void showLostModules(ModularDesign incoming){
        if(design == null || design.isEmpty() || incoming == null) return;

        ObjectSet<String> kept = new ObjectSet<>();
        for(PlacedModule m : incoming.modules) kept.add(cargoKey(m));
        for(PlacedModule m : design.modules){
            if(!kept.contains(cargoKey(m))) shedEffects(m);
        }
    }

    private void blowOffCannon(PlacedModule hinge){
        modulePos(hinge, Tmp.v6);
        float hx = Tmp.v6.x, hy = Tmp.v6.y;

        float aim = rotation - 90f;
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof CannonWeapon cannon && cannon.placed == hinge){
                aim += mount.rotation;
                break;
            }
        }

        float cell = ModularUnitType.cellWorld();
        ModularDesign cannon = hinge.cannon;
        Fx.dynamicExplosion.at(hx, hy, 1f + Math.min(cannon.totalWeight() / 20f, 3f));
        Sounds.explosionDull.at(hx, hy, Mathf.random(0.6f, 0.8f));
        Effect.shake(Math.min(2f + cannon.totalWeight() / 10f, 10f), 20f, hx, hy);

        for(PlacedModule part : cannon.modules){
            float ox = (part.x + part.type.w / 2f - cannon.pivot) * cell, oy = (part.y + part.type.h / 2f - cannon.pivot) * cell;
            float px = hx + Angles.trnsx(aim, ox, oy), py = hy + Angles.trnsy(aim, ox, oy);
            float angle = Mathf.zero(Mathf.dst(px, py, hx, hy), 0.01f) ? Mathf.random(360f) : Angles.angle(hx, hy, px, py) + Mathf.range(50f);
            launchDebris(part.type, px, py, angle, Mathf.random(30f, 80f));
        }
    }

    public void launchDebris(PlacedModule m){
        modulePos(m, Tmp.v1);
        float sx = Tmp.v1.x, sy = Tmp.v1.y;

        float angle = Mathf.zero(Tmp.v1.dst(x, y), 0.01f)
            ? Mathf.random(360f)
            : Angles.angle(x, y, sx, sy) + Mathf.range(30f);
        launchDebris(m.type, sx, sy, angle, Mathf.random(16f, 42f));
    }

    private void launchDebris(ModuleType type, float sx, float sy, float angle, float dist){
        float spin = Mathf.range(240f);

        MdlFX.moduleDebrisFly.at(sx, sy, angle, Color.white, new MdlFX.Debris(type, dist, spin));

        float lx = sx + Angles.trnsx(angle, dist);
        float ly = sy + Angles.trnsy(angle, dist);
        Time.run(MdlFX.debrisFlyTime, () ->
            MdlFX.moduleDebrisRest.at(lx, ly, spin, Color.white, type));
    }

    public boolean hasCannonMount(PlacedModule hinge){
        if(mounts == null) return false;
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof CannonWeapon cannon && cannon.placed == hinge) return true;
        }
        return false;
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
        boolean full = Time.time - designChangedAt < designFreshTicks
            || (int)((Time.time + id * 37f) / designWindowTicks) % designRefreshWindows == 0;
        write.bool(full);
        if(full) write.str(design == null ? "" : design.serialize());
        write.str(cargoData());
        write.i(shedCount);
        for(int i = 0; i < 2; i++){
            write.i(towUnitIds[i]); write.i(towXs[i]); write.i(towYs[i]);
            write.i(towOtherXs[i]); write.i(towOtherYs[i]); write.f(towLengths[i]); write.f(towTensions[i]);
        }
        writeActives(write);
    }

    @Override
    public void readSync(Reads read){
        super.readSync(read);
        boolean full = read.bool();
        String designString = full ? read.str() : null;
        String incomingCargo = read.str();
        int incomingShed = read.i();
        for(int i = 0; i < 2; i++){
            towUnitIds[i] = read.i(); towXs[i] = read.i(); towYs[i] = read.i();
            towOtherXs[i] = read.i(); towOtherYs[i] = read.i(); towLengths[i] = read.f(); towTensions[i] = read.f();
        }

        if(full){
            ModularDesign incoming = ModularDesign.read(designString);
            String cur = design == null ? "" : design.serialize();
            if(!cur.equals(designString) || shedCount != incomingShed){
                if(incomingShed > shedCount) showLostModules(incoming);
                applyDesignState(incoming, incomingShed);
            }
        }
        readCargoData(incomingCargo);
        readActives(read);
    }
}
