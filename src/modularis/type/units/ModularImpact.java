package modularis.type.units;

import arc.math.*;
import arc.math.geom.*;
import mindustry.entities.bullet.*;

import modularis.type.units.modules.*;

public class ModularImpact{
    // ---- mass ----
    public static final float massScale = 42f;
    public static final float minMass = 120f;

    // ---- round carries ----
    /** Momentum = sqrt(damage) * speed * this. Square root, because damage tracks energy (mv^2/2). */
    public static final float momentumScale = 4.2f;
    /** Splash damage shoves too, but a blast pushes rather than punches. */
    public static final float splashMomentum = 14f;
    /** No single hit may shove a machine harder than this fraction of its own top speed. */
    public static final float maxImpulseSpeed = 0.45f;

    // ---- sloped armour ----
    public static final float deflectStart = 25f;
    public static final float hullRadiusScale = 0.62f;
    /** Damage a fully grazing hit still gets through, before armour. */
    public static final float minDeflect = 0.3f;
    /** A dead-on hit concentrates on one plate, so it bites slightly harder. */
    public static final float normalBonus = 1.12f;
    /** Plating rating at which the deflection window is fully open. */
    public static final float armorReference = 8f;
    /** Best chance a grazing round has of skating off entirely, on very heavy plate. */
    public static final float maxRicochet = 0.9f;
    public static final float ricochetDamage = 0.12f;
    public static final float spallDamage = 0.4f;
    /** How far outside the plate the deflected round is placed, so it does not re-hit. */
    public static final float spallOffset = 3f;
    /** Spread on the deflected line - plating is not a mirror. */
    public static final float spallSpread = 14f;

    // ---- spin ----
    public static final float spinScale = 3f;
    public static final float maxSpin = 4f;
    public static final float spinDamping = 0.16f;
    public static final float spinEpsilon = 0.01f;
    public static final float spinGripHold = 0.2f;

    // ---- gun recoil ----
    public static final float recoilTransfer = 1f;

    // ---- modules hit ----
    public static final float minShedDamage = 8f;
    public static final float armorToughness = 45f;
    public static final float shedScale = 0.3f;
    /** How sharply the chance climbs with damage relative to what the round landed on. */
    public static final float shedExponent = 1.5f;
    /** No single hit is ever more likely than this to take a module off. */
    public static final float maxShedChance = 0.35f;
    /** Hit size the editor quotes the blow-off risk against. */
    public static final float shedReferenceDamage = 100f;

    // ---- finding the module a round ran into ----
    /** Ray march step through the design, in cells. */
    public static final float rayStep = 0.34f;
    /** Hard cap on the march, so a round spotted far out can't walk forever. */
    public static final int maxRaySteps = 220;

    // ---- ramming ----
    /** Damage per unit of (weight * speed^2). */
    public static final float ramScale = 1.6f;
    /** Closing speed below which a bump is just a bump. */
    public static final float ramMinSpeed = 0.35f;
    /** Ticks between two hulls being able to hurt each other again. */
    public static final float ramInterval = 26f;
    /** Share of the ram that comes back into the rammer, scaled by the mass it hit. */
    public static final float ramSelfDamage = 0.4f;
    /** Closing speed a ram counts as at most while dashing; damage grows with its square. */
    public static final float dashRamSpeed = 1.5f;
    public static final float crashMinSpeed = 0.45f;
    public static final float crashInterval = 30f;
    public static final float crashBuildingSelf = 0.6f;
    public static final float ramContactMemory = 45f;
    public static final float ramSustainedShare = 0.05f;
    public static final float ramBlockCone = 75f;

    // ---- hulls touching ----
    public static final float pushSoftness = 1.25f;
    public static final float maxPushStep = 3f;
    public static final float contactRestitution = 0.1f;

    public static float contactShare(float ownMass, float theirMass, boolean selfPinned, boolean otherPinned){
        if(otherPinned) return 1f;
        if(selfPinned) return 0f;
        return theirMass / Math.max(ownMass + theirMass, 1f);
    }

    public static float contactStop(float closing){
        return closing < 0f ? -closing * (1f + contactRestitution) : 0f;
    }

    public static final float contactNormalMemory = 20f;
    public static final float flipClosing = 0.05f;

    public static boolean flipNormal(float nx, float ny, float closing, boolean remembered, float rx, float ry, float awayX, float awayY){
        if(remembered) return nx * rx + ny * ry < 0f;
        return closing > flipClosing && nx * awayX + ny * awayY < 0f;
    }

    public static float contactStep(float depth, float share){
        return Math.min(depth * share / pushSoftness, maxPushStep);
    }

    public static float toughness(ModuleType type){
        return Math.max((type.health + type.armor * armorToughness) * type.shedResistance, 1f);
    }

    public static float shedChance(float damage, ModuleType hit){
        if(hit == null || damage < minShedDamage) return 0f;
        return chanceFor(damage, toughness(hit));
    }

    public static float shedChance(float damage, PlacedModule hit){
        if(hit == null || damage < minShedDamage) return 0f;
        float toughness = Math.max((hit.health() + hit.type.armor * armorToughness) * hit.type.shedResistance, 1f);
        return chanceFor(damage, toughness);
    }

    private static float chanceFor(float damage, float toughness){
        float ratio = damage / toughness;
        return Math.min(shedScale * (float)Math.pow(ratio, shedExponent), maxShedChance);
    }

    public static float mass(float weight){
        return Math.max(minMass, weight * massScale);
    }

    public static float momentum(BulletType type, float damage, float speed){
        if(type == null) return 0f;
        float solid = Mathf.sqrt(Math.max(damage, 0f)) * Math.max(speed, 0.1f) * momentumScale;
        float blast = Mathf.sqrt(Math.max(type.splashDamage, 0f)) * splashMomentum;
        return solid + blast;
    }

    public static float incidence(float centerX, float centerY, float fromX, float fromY, float travelAngle, float radius){
        if(radius < 0.001f) return 1f;

        float tx = Mathf.cosDeg(travelAngle), ty = Mathf.sinDeg(travelAngle);
        float ox = centerX - fromX, oy = centerY - fromY;

        float miss = Math.abs(tx * oy - ty * ox);
        float ratio = Mathf.clamp(miss / radius, 0f, 1f);
        return Mathf.sqrt(1f - ratio * ratio);
    }

    public static float incidenceAngle(float incidence){
        return (float)Math.acos(Mathf.clamp(incidence, 0f, 1f)) * Mathf.radDeg;
    }

    public static float deflectMultiplier(float incidence, float armor){
        float angle = incidenceAngle(incidence);
        if(angle <= deflectStart) return Mathf.lerp(1f, normalBonus, 1f - angle / deflectStart);

        float over = (angle - deflectStart) / (90f - deflectStart);
        //thin plate barely turns anything; heavy plate uses the whole window
        float floor = Mathf.lerp(1f, minDeflect, armorFactor(armor));
        return Mathf.lerp(1f, floor, over * over);
    }

    public static float ricochetChance(float incidence, float armor){
        float angle = incidenceAngle(incidence);
        if(angle <= deflectStart) return 0f;

        float over = (angle - deflectStart) / (90f - deflectStart);
        return over * over * maxRicochet * armorFactor(armor);
    }

    public static float armorFactor(float armor){
        return Mathf.clamp(armor / armorReference, 0f, 1f);
    }

    public static float spinFrom(float leverX, float leverY, float impX, float impY, float inertia){
        float cell = ModularUnitType.cellWorld();
        //Stats.inertia is measured in cells; the lever arm here is in world units
        float worldInertia = Math.max(inertia * cell * cell, 1f);
        float torque = leverX * impY - leverY * impX;
        return Mathf.clamp(torque / worldInertia * spinScale, -maxSpin, maxSpin);
    }

    public static float ramDamage(float weight, float closingSpeed){
        if(closingSpeed <= ramMinSpeed) return 0f;
        return ramScale * Math.max(weight, 0.1f) * closingSpeed * closingSpeed;
    }

    public static Vec2 clampImpulse(Vec2 out, float mass, float topSpeed){
        float cap = mass * Math.max(topSpeed, 0.2f) * maxImpulseSpeed;
        if(out.len2() > cap * cap) out.setLength(cap);
        return out;
    }
}
