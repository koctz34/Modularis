package modularis.type.units;

import arc.math.*;

import modularis.type.units.modules.*;

public class ModularPhysics{
    /** Base world speed (UnitType.speed) corresponding to a speed rating of 1. */
    public static final float baseSpeed = 1.15f;

    // ---- mass -> top-speed ceiling ----
    /** At or below this total weight there is no weight speed penalty. */
    public static final float lightWeight = 9f;
    /** At or above this total weight the weight penalty is maxed out. */
    public static final float heavyWeight = 55f;
    /** Top-speed fraction a very heavy machine is limited to. */
    public static final float minWeightSpeed = 0.18f;
    /** Fraction of the weight speed penalty that still applies to hovering machines. */
    public static final float hoverWeightRelief = 0.5f;

    // ---- capacity -> load ----
    /** How sharply mobility drops once weight exceeds drive capacity. */
    public static final float loadExponent = 1.6f;

    // ---- balance (centre of mass vs drive centroid) ----
    /** Imbalance (as a fraction of machine size) that is tolerated for free. */
    public static final float balanceTolerance = 0.15f;
    /** How hard imbalance beyond the tolerance bites. */
    public static final float balancePenalty = 1.1f;
    /** Worst-case speed fraction for a hopelessly unbalanced machine. */
    public static final float minBalance = 0.25f;

    // ---- acceleration (force / mass) ----
    public static final float baseAccel = 0.11f;
    /** Force-to-mass ratio treated as "properly engined". Measured mid-range for good designs. */
    public static final float accelReference = 0.9f;
    public static final float minAccel = 0.022f, maxAccel = 0.30f;

    // ---- turning (torque / inertia) ----
    public static final float baseTurnRate = 2.2f;

    public static final float turnReference = 1.1f;
    public static final float minTurnFactor = 0.22f, maxTurnFactor = 1.5f;

    public static final float turnBalanceFloor = 0.65f;

    public static final float steerLever = 2f;

    public static final float referenceDiscLoading = 0.6f;
    public static final float minLiftEfficiency = 0.35f, maxLiftEfficiency = 1.4f;

    public static final float torqueTolerance = 0.15f;

    public static Stats compute(ModularDesign design){
        Stats s = new Stats();
        s.weight = design.totalWeight();

        //machine's weight each drive part actually carries
        float driveSum = 0f;

        //centre of mass (weight-weighted) and drive centroid (capacity-weighted), in cells
        float comX = 0f, comY = 0f, massSum = 0f;
        float driveX = 0f, driveY = 0f;

        //convertor multipliers that get folded in once the raw totals are known
        float weightMult = 1f, haulMult = 1f, prodMult = 1f, useMult = 1f;

        //C4: the single biggest charge sets the base blast size, the count scales it up
        float c4Radius = 0f;

        for(PlacedModule pm : design.modules){
            ModuleType t = pm.type;
            float mcx = pm.x + t.w / 2f, mcy = pm.y + t.h / 2f;

            if(t.category == ModuleCategory.root) s.hasRoot = true;

            float mass = Math.max(t.weight, 0.0001f);
            comX += mcx * mass;
            comY += mcy * mass;
            massSum += mass;
            s.armor += t.armor;

            //everything past here is the module's FUNCTION, which needs a slot to run in
            if(!design.isActive(pm)){
                s.inactiveCount++;
                continue;
            }

            s.powerProd += t.powerProduction;
            s.powerUse += t.powerUse;
            s.cargoCapacity += t.cargoCapacity;
            s.buildSpeed += t.buildSpeed;

            if(t instanceof ModulDrill d){
                //the best drill sets what ore we can cut and how far we reach; speeds stack
                s.drillTier = Math.max(s.drillTier, d.tier);
                s.drillSpeed += d.drillSpeed;
                s.drillRange = Math.max(s.drillRange, d.mineRange);
            }

            //ANY module may bend the machine's stats - convertors and turbos are just
            //modules whose multipliers happen to be interesting. They all stack.
            s.healthMultiplier *= t.healthMultiplier;
            s.damageMultiplier *= t.damageMultiplier;
            s.reloadMultiplier *= t.reloadMultiplier;
            s.speedMod *= t.speedMultiplier;
            weightMult *= t.weightMultiplier;
            haulMult *= t.haulMultiplier;
            prodMult *= t.powerProductionMultiplier;
            useMult *= t.powerUseMultiplier;

            if(t.booster){
                s.hasBooster = true;
                s.boostMaxWeight = Math.max(s.boostMaxWeight, t.boostMaxWeight);
                s.boostMultiplier *= t.boostMultiplier;
            }

            if(t instanceof ModulPropulsor p){
                float haul = Math.max(p.haulWeight, 0.001f);
                s.hasWheels = true;
                s.wheelCount++;
                s.capacity += haul;
                driveSum += p.moveSpeed * haul;
                driveX += mcx * haul;
                driveY += mcy * haul;

                switch(p.mode){
                    case hover -> {
                        s.hasHover = true;
                        if(p instanceof ModulHover h){
                            s.hoverMaxWeight = Math.max(s.hoverMaxWeight, h.maxWeight);
                        }
                    }
                    case air -> {
                        s.hasRotor = true;
                        s.lift += p.lift();
                        if(p instanceof ModulRotor r){
                            s.discArea += r.discArea();
                            s.rotorTorque += r.counterTorque;
                            s.rotorTorqueAbs += Math.abs(r.counterTorque);
                        }
                    }
                    default -> s.hasGroundDrive = true;
                }
            }else if(t instanceof ModulC4 c4){
                //more charges = more damage, and a bigger (but sub-linear) blast
                s.c4Count++;
                s.blastDamage += c4.damage;
                c4Radius = Math.max(c4Radius, c4.radius);
                s.detonateRange = Math.max(s.detonateRange, c4.detonateRange);
            }
        }

        if(massSum > 0.0001f){
            comX /= massSum;
            comY /= massSum;
        }
        s.centerX = comX;
        s.centerY = comY;

        //RAW capacity: the drivetrain's own numbers, before any convertor scaling. Top speed
        //and the drive centroid are ratios over these, so scaling capacity must not touch them.
        float rawCapacity = s.capacity;

        // 3. drivetrain top speed = capacity-weighted average
        s.topRating = rawCapacity > 0.0001f ? driveSum / rawCapacity : 0f;

        // 4. balance: how far the mass sits from the drive parts holding it up
        if(rawCapacity > 0.0001f){
            driveX /= rawCapacity;
            driveY /= rawCapacity;

            //normalise the offset by the machine's own size, so "far" scales with the build
            float span = Math.max(1f, Math.max(design.widthCells(), design.heightCells()) * 0.5f);
            s.balanceOffset = Mathf.dst(comX, comY, driveX, driveY) / span;

            float excess = Math.max(0f, s.balanceOffset - balanceTolerance);
            s.balanceFactor = Mathf.clamp(1f - excess * balancePenalty, minBalance, 1f);
        }else{
            s.balanceOffset = 0f;
            s.balanceFactor = 0f;
        }

        //blast radius scales with the square root of the charge count, like real explosives
        s.blastRadius = s.c4Count > 0 ? c4Radius * (float)Math.sqrt(s.c4Count) : 0f;

        //now fold in the convertor multipliers
        s.weight *= weightMult;
        s.capacity = rawCapacity * haulMult;
        s.powerProd *= prodMult;
        s.powerUse *= useMult;

        s.hoverOverweight = s.hasHover && s.weight > s.hoverMaxWeight;
        s.boostOverweight = s.hasBooster && s.weight > s.boostMaxWeight;

        // 5. power satisfaction
        s.powerRatio = s.powerUse <= 0.0001f ? 1f : Mathf.clamp(s.powerProd / s.powerUse, 0f, 1f);

        // ---- pass 2: inertia and traction, both measured about the centre of mass ----
        float inertia = 0f, steerTorque = 0f, driveForce = 0f;
        for(PlacedModule pm : design.modules){
            ModuleType t = pm.type;
            float mcx = pm.x + t.w / 2f, mcy = pm.y + t.h / 2f;
            float lever = Mathf.dst(mcx, mcy, comX, comY);

            inertia += Math.max(t.weight, 0.0001f) * lever * lever;

            if(!design.isActive(pm) || !(t instanceof ModulPropulsor p)) continue;

            float haul = Math.max(p.haulWeight, 0.001f);

            float share = rawCapacity > 0.0001f ? haul / rawCapacity : 0f;
            float normal = s.weight * share;
            float traction = p.mode == PropulsionMode.hover ? p.thrust() : p.grip * normal;
            driveForce += Math.min(p.thrust(), traction);
            s.tractionForce += traction;

            //steering torque: grip acting at a lever arm. Parts bunched around the centre of
            //mass barely swing the hull; ones out at the ends of it swing it hard.
            steerTorque += p.rotateSpeed * haul * p.grip * Mathf.clamp(lever / steerLever, 0.25f, 1.6f);
        }
        s.inertia = inertia;
        s.steerTorque = steerTorque;
        s.driveForce = driveForce * s.powerRatio;

        // 2. load: is there enough capacity to bear the mass?
        if(s.capacity <= 0.0001f || s.weight <= 0.0001f){
            s.loadFactor = 0f;
        }else{
            float support = Mathf.clamp(s.capacity / s.weight, 0f, 1f);
            s.loadFactor = (float)Math.pow(support, loadExponent);
        }

        // 1. absolute mass sets the top-speed ceiling
        float wt = Mathf.clamp((s.weight - lightWeight) / (heavyWeight - lightWeight), 0f, 1f);
        s.weightFactor = Mathf.lerp(1f, minWeightSpeed, wt);

        if(s.hovering()){
            s.weightFactor = 1f - (1f - s.weightFactor) * hoverWeightRelief;
        }

        s.speedRating = s.topRating * s.loadFactor * s.weightFactor * s.balanceFactor
            * s.powerRatio * s.speedMod;

        // ---- acceleration: F = ma, normalised into the engine's accel field ----
        float forceRatio = s.weight > 0.0001f ? s.driveForce / s.weight : 0f;
        s.accelRating = Mathf.clamp(baseAccel * forceRatio / accelReference, minAccel, maxAccel);

        // ---- turn rate: angular acceleration is torque over inertia ----
        if(inertia > 0.0001f && steerTorque > 0.0001f){
            s.turnFactor = Mathf.clamp(steerTorque / inertia / turnReference, minTurnFactor, maxTurnFactor);
        }else{
            s.turnFactor = 0f;
        }
        s.turnRate = baseTurnRate * s.turnFactor * s.powerRatio
            * Mathf.lerp(turnBalanceFloor, 1f, s.balanceFactor);

        // ---- 8. lift (rotors) ----
        if(s.hasRotor){
            s.discLoading = s.discArea > 0.0001f ? s.weight / s.discArea : Float.MAX_VALUE;
            s.liftEfficiency = Mathf.clamp(referenceDiscLoading / Math.max(s.discLoading, 0.0001f),
                minLiftEfficiency, maxLiftEfficiency);

            s.effectiveLift = s.lift * s.liftEfficiency * s.powerRatio;
            s.liftRatio = s.weight > 0.0001f ? s.effectiveLift / s.weight : 0f;

            s.torqueImbalance = s.rotorTorqueAbs > 0.0001f
                ? Math.abs(s.rotorTorque) / s.rotorTorqueAbs : 0f;
        }

        s.overloaded = s.capacity > 0f && s.weight > s.capacity;
        s.underpowered = s.powerRatio < 0.999f;
        s.unbalanced = s.hasWheels && s.balanceFactor < 0.95f;
        s.slipping = s.hasGroundDrive && s.driveForce < s.weight * 0.12f;

        s.immobile = !s.hasRoot || !s.hasWheels || s.speedRating < 0.02f || s.hoverOverweight;
        return s;
    }

    /** Computed movement stats for a design. */
    public static class Stats{
        public boolean hasRoot, hasWheels, overloaded, underpowered, unbalanced, immobile;
        /** True if the drivetrain can barely put its force down - wheels spinning under load. */
        public boolean slipping;

        /** Centre of mass, in design cells. */
        public float centerX, centerY;

        /** Has hover. */
        public boolean hasHover;
        /** Weight limit of the strongest hover. */
        public float hoverMaxWeight;
        /** True if hovers are present but the machine is too heavy to lift. */
        public boolean hoverOverweight;
        /** True if the design also carries a wheel/track that needs to touch the ground. */
        public boolean hasGroundDrive;

        public boolean hovering(){
            return hasHover && !hoverOverweight && !hasGroundDrive;
        }

        //---- rotors (groundwork; see ModulRotor) ----
        /** Has at least one rotor. */
        public boolean hasRotor;
        /** Raw summed lift rating. */
        public float lift;
        /** Lift after disc efficiency and available power. */
        public float effectiveLift;
        /** Effective lift over mass. Must exceed 1 to leave the ground. */
        public float liftRatio;
        /** Summed rotor disc area, in cells^2. */
        public float discArea;
        /** Weight carried per cell^2 of rotor disc. Lower is more efficient. */
        public float discLoading;
        /** Lift multiplier from disc loading. */
        public float liftEfficiency;
        /** Signed rotor torque; opposite-spinning rotors cancel. */
        public float rotorTorque;
        /** Total absolute rotor torque, i.e. how much there is to cancel in the first place. */
        public float rotorTorqueAbs;
        public float torqueImbalance;

        /** True when rotors could actually carry this machine (and nothing pins it to the floor). */
        public boolean airborne(){
            return hasRotor && !hasGroundDrive && liftRatio >= 1f
                && torqueImbalance <= torqueTolerance;
        }

        /** How this machine moves, resolved from the parts it carries. */
        public PropulsionMode mode(){
            if(airborne()) return PropulsionMode.air;
            if(hovering()) return PropulsionMode.hover;
            return PropulsionMode.ground;
        }

        /** Has at least one booster. */
        public boolean hasBooster;
        public float boostMaxWeight;
        public boolean boostOverweight;
        public float boostMultiplier = 1f;

        public boolean canBoost(){
            return hasBooster && !boostOverweight;
        }

        public int wheelCount;
        /** Modules bolted on but with no slot to run in: dead weight, no function. */
        public int inactiveCount;
        public float weight, capacity;
        public float loadFactor, weightFactor, balanceFactor, balanceOffset;
        public float powerProd, powerUse, powerRatio;
        public float topRating, speedRating;

        //---- acceleration ----
        /** Usable drive force after grip and power: min(thrust, traction) summed over the parts. */
        public float driveForce;
        /** Total force the drivetrain could transmit if the engines could supply it. */
        public float tractionForce;
        /** Ready-made {@code UnitType.accel}. */
        public float accelRating;

        //---- turning ----
        /** Rotational inertia about the centre of mass, sum(m * r^2). */
        public float inertia;
        /** Steering torque the drive parts can generate. */
        public float steerTorque;
        /** Handling quality, 1 = nominal. */
        public float turnFactor;
        /** Ready-made {@code UnitType.rotateSpeed}, in degrees per tick. */
        public float turnRate;

        /** Summed plating. */
        public float armor;
        /** Summed item capacity. */
        public int cargoCapacity;
        /** Summed build speed. 0 = the machine cannot build at all. */
        public float buildSpeed;

        /** Highest ore hardness the machine's drills can cut. -1 = no drill at all. */
        public int drillTier = -1;
        /** Summed drill speed. */
        public float drillSpeed;
        /** Longest drill reach, measured from the machine's hull. */
        public float drillRange;

        /** A machine can only mine if it carries at least one drill. */
        public boolean canMine(){
            return drillTier >= 0 && drillSpeed > 0f;
        }

        /** A machine can only build if it carries at least one build module. */
        public boolean canBuild(){
            return buildSpeed > 0f;
        }

        //---- stat multipliers, stacked from every active module (1 = untouched) ----
        /** Applied to the machine's max health when the design is assigned. */
        public float healthMultiplier = 1f;
        /** Fed to {@code unit.damageMultiplier} each tick. */
        public float damageMultiplier = 1f;
        /** Fed to {@code unit.reloadMultiplier} each tick. */
        public float reloadMultiplier = 1f;
        /** Extra top-speed multiplier, folded into {@link #speedRating}. */
        public float speedMod = 1f;

        //---- C4 (kamikaze) ----
        /** Number of C4 charges aboard. Any at all makes the machine a kamikaze. */
        public int c4Count;
        /** Total blast damage (sums across charges). */
        public float blastDamage;
        /** Blast radius, grown from the biggest charge by sqrt(count). */
        public float blastRadius;
        /** Reach beyond the hitbox at which an enemy sets it off. */
        public float detonateRange;

        public boolean isKamikaze(){
            return c4Count > 0;
        }

        /** True if any module is actually bending the machine's stats. */
        public boolean hasMultipliers(){
            return !Mathf.equal(healthMultiplier, 1f, 0.001f)
                || !Mathf.equal(damageMultiplier, 1f, 0.001f)
                || !Mathf.equal(reloadMultiplier, 1f, 0.001f)
                || !Mathf.equal(speedMod, 1f, 0.001f);
        }

        public float speedMultiplier(){
            return immobile ? 0f : speedRating;
        }

        public float dragMultiplier(){
            if(capacity <= 0f) return 1f;
            float load = weight / capacity;
            return Mathf.clamp(1f + Math.max(0f, load - 1f) * 0.6f, 1f, 3f);
        }

        /**
         * Weapon fire rate: the convertor multiplier scaled by how well the machine is
         * powered. Starve the turrets of power and they cycle slower and slower.
         */
        public float fireRateMultiplier(){
            return reloadMultiplier * powerRatio;
        }

        /**
         * Turrets need a command core to aim them, and at least a trickle of power to cycle.
         * Without either, the machine is disarmed outright.
         */
        public boolean canShoot(){
            return hasRoot && powerRatio > 0.02f;
        }

        /** Effective top speed in tiles/second, for display. */
        public float speedTiles(){
            return speedMultiplier() * baseSpeed * 60f / 8f;
        }

        public float loadPercent(){
            return capacity <= 0f ? 0f : weight / capacity * 100f;
        }

        /** Weight-imposed speed ceiling, as a percentage of the drivetrain's top speed. */
        public float weightSpeedPercent(){
            return weightFactor * 100f;
        }

        /** Balance quality, as a percentage (100% = mass sits right over the drive parts). */
        public float balancePercent(){
            return balanceFactor * 100f;
        }

        /** Handling, as a percentage (100% = nominal turn rate for this mass and shape). */
        public float turnPercent(){
            return turnFactor * 100f;
        }

        /** Seconds to reach top speed, roughly - for display only. */
        public float timeToSpeed(){
            return accelRating <= 0.0001f ? 0f : 1f / (accelRating * 60f);
        }
    }
}
