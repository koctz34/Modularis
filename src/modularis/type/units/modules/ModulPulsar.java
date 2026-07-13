package modularis.type.units.modules;

import arc.audio.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.gen.*;

import modularis.content.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

/**
 * Ability module - a pulsar. Periodically emits a pulse in a radius around itself and
 * applies whatever effects it is configured with. A mender is just a pulsar whose
 * {@link #healAmount} is set; everything else is off by default.
 *
 * Every effect defaults to 0/none, so in content you only set the knobs you actually want:
 * <pre>
 * mender = add(new ModulPulsar("mender1x1"){{
 *     reload = 200f;
 *     pulseRange = 100f;
 *     healAmount = 55f;
 * }});
 *
 * ripper = add(new ModulPulsar("ripper1x1"){{
 *     reload = 240f;
 *     pulseRange = 80f;
 *     damage = 40f;
 *     tearChance = 0.35f;   //rips a module off enemy machines
 * }});
 * </pre>
 */
public class ModulPulsar extends ModuleType{
    /** Ticks between pulses. */
    public float reload = 120f;
    /** Pulse radius, in world units. */
    public float pulseRange = 90f;

    /** Shield radius in world units. 0 = no shield. */
    public float shieldRadius = 0f;
    /** Shield HP regenerated per tick. */
    public float shieldRegen = 0.4f;
    /** Maximum shield HP. */
    public float shieldMax = 500f;
    /** Ticks the shield stays down after being broken. */
    public float shieldCooldown = 60f * 6f;
    /** Number of sides on the shield polygon. */
    public int shieldSides = 6;
    /** Shield polygon rotation. */
    public float shieldRotation = 0f;

    /** HP restored to each ALLY caught in the pulse. */
    public float healAmount = 0f;
    /** Fraction of max HP restored to each ally (0.1 = 10%). */
    public float healPercent = 0f;
    /** Damage dealt to ENEMIES caught in the pulse. */
    public float damage = 0f;
    /** Status effect applied to enemies caught in the pulse. */
    public @Nullable mindustry.type.StatusEffect status;
    /** How long {@link #status} lasts, in ticks. */
    public float statusDuration = 120f;

    /**
     * Chance (0..1) that the pulse rips a module clean off each enemy modular machine in
     * range - the same way modules are torn off by damage. Turrets and the command core
     * are never ripped.
     */
    public float tearChance = 0f;

    public Color pulseColor = Color.valueOf("84f491");
    public @Nullable Sound pulseSound;
    public float pulseSoundVolume = 0.7f;
    public boolean hasPulseEffect = true;

    public ModulPulsar(String name){
        super(name);
        category = ModuleCategory.ability;
        slot = SlotType.ability;
        weight = 1.5f;
        health = 90f;
        powerUse = 0.7f;
    }

    public boolean hasShield(){
        return shieldRadius > 0f && shieldMax > 0f;
    }

    public @Nullable Ability createShield(){
        if(!hasShield()) return null;
        return new ForceFieldAbility(shieldRadius, shieldRegen, shieldMax, shieldCooldown,
            shieldSides, shieldRotation);
    }

    public void updatePulse(ModularUnitEntity unit, PulsarMount mount, float x, float y){
        mount.charge += Time.delta;
        if(mount.charge < reload) return;
        mount.charge = 0f;

        if(hasPulseEffect){
            MdlFX.menderPulse.at(x, y, pulseRange, pulseColor);
        }
        if(pulseSound != null) pulseSound.at(x, y, 1f, pulseSoundVolume);

        if(net.client()) return;

        if(healAmount > 0f || healPercent > 0f){
            Units.nearby(unit.team, x, y, pulseRange, other -> {
                if(!other.damaged()) return;
                other.heal(healAmount + other.maxHealth() * healPercent);
            });
        }

        if(damage > 0f){
            Damage.damage(unit.team, x, y, pulseRange, damage);
        }

        if(status != null || tearChance > 0f){
            Units.nearbyEnemies(unit.team, x, y, pulseRange, other -> {
                if(status != null) other.apply(status, statusDuration);

                if(tearChance > 0f && other instanceof ModularUnitEntity victim
                    && Mathf.chance(tearChance)){
                    victim.tearOffModule();
                }
            });
        }
    }

    @Override
    public void buildStats(Table table){
        stat(table, "Range", Strings.autoFixed(pulseRange / 8f, 1) + " tiles");
        stat(table, "Interval", Strings.autoFixed(reload / 60f, 1) + "s");
        if(healAmount > 0f) stat(table, "Heal", Strings.autoFixed(healAmount, 0) + " HP");
        if(healPercent > 0f) stat(table, "Heal", Strings.autoFixed(healPercent * 100f, 0) + "% HP");
        if(damage > 0f) stat(table, "Damage", Strings.autoFixed(damage, 0));
        if(tearChance > 0f){
            stat(table, "Rip module", "[scarlet]" + Strings.autoFixed(tearChance * 100f, 0) + "%[]");
        }
        if(hasShield()){
            stat(table, "Shield", "[cyan]" + Strings.autoFixed(shieldMax, 0) + " HP[]");
            stat(table, "Shield radius", Strings.autoFixed(shieldRadius / 8f, 1) + " tiles");
            stat(table, "Shield regen", Strings.autoFixed(shieldRegen * 60f, 1) + "/s");
            stat(table, "Shield downtime", Strings.autoFixed(shieldCooldown / 60f, 1) + "s");
        }
    }
}
