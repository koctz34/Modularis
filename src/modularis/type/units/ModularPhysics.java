package modularis.type.units;

import arc.math.*;

import modularis.type.units.modules.*;

/**
 * Turns a {@link ModularDesign} into concrete movement stats.
 *
 * The model, in words:
 *
 *  1. MASS. Every module has weight. The machine's absolute total weight sets a top-speed
 *     ceiling: a light buggy can hit the drivetrain's full speed, a heavy tank is capped
 *     far below it no matter how much drivetrain you bolt on.
 *
 *  2. CAPACITY. Wheels/tracks each haul a limited amount of weight. Summed, that's the
 *     machine's load capacity. Not enough capacity for the mass => overloaded, it crawls.
 *     Surplus capacity is not free speed - it's headroom to carry more armour.
 *
 *  3. DRIVETRAIN SPEED. The mechanical top speed is the CAPACITY-WEIGHTED AVERAGE of the
 *     drive parts, not the fastest one: a slow high-capacity track carries most of the load
 *     and so dominates the speed. Bolting one fast wheel onto a tracked hull nudges the
 *     average instead of lifting the whole machine to wheel speed.
 *
 *  4. BALANCE. The drive parts must sit under the centre of mass. We compare the machine's
 *     centre of mass against the capacity-weighted centroid of its drive parts; the further
 *     apart they are (relative to the machine's size), the worse it drives. This is what
 *     stops "one big track bolted to one side" from being optimal - the mass hangs off the
 *     drivetrain, so it drags instead of driving.
 *
 *  5. POWER. Engines produce it; drive parts, the core and weapons consume it. Short on
 *     power and everything runs at a fraction of its rating.
 *
 */
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

    public static Stats compute(ModularDesign design){
        Stats s = new Stats();
        s.weight = design.totalWeight();

        //drivetrain: Σ(speed * capacity), so top speed can be averaged by how much of the
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

            if(t instanceof ModulWheel w){
                float haul = Math.max(w.haulWeight, 0.001f);
                s.hasWheels = true;
                s.wheelCount++;
                s.capacity += haul;
                driveSum += w.moveSpeed * haul;
                driveX += mcx * haul;
                driveY += mcy * haul;

                if(w instanceof ModulHover h){
                    s.hasHover = true;
                    s.hoverMaxWeight = Math.max(s.hoverMaxWeight, h.maxWeight);
                }else{
                    s.hasGroundDrive = true;
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

        s.overloaded = s.capacity > 0f && s.weight > s.capacity;
        s.underpowered = s.powerRatio < 0.999f;
        s.unbalanced = s.hasWheels && s.balanceFactor < 0.95f;

        s.immobile = !s.hasRoot || !s.hasWheels || s.speedRating < 0.02f || s.hoverOverweight;
        return s;
    }

    /** Computed movement stats for a design. */
    public static class Stats{
        public boolean hasRoot, hasWheels, overloaded, underpowered, unbalanced, immobile;
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

        /** True if any convertor is actually changing something. */
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
    }
}
