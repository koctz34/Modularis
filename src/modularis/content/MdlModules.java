package modularis.content;

import arc.graphics.*;
import arc.struct.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;

import modularis.type.units.modules.*;

public class MdlModules{
    /** All registered modules, in registration order. */
    public static final Seq<ModuleType> all = new Seq<>();
    /** Fast name -> module lookup, used by design (de)serialization. */
    public static final ObjectMap<String, ModuleType> nameMap = new ObjectMap<>();

    public static ModuleType
        // base / armour
        basePanel, baseLong, baseBig,
        // control
        root, rootMedium,
        // power
        engine, engineMedium, fuelCell,
        // movement
        wheel, track, trackBig,
        // weapons
        gun,
        // abilities
        mender, turboHeater;

    public static void load(){
        if(!all.isEmpty()) return;

        // ---- Base (armour panels) ----
        basePanel = add(new ModulBase("base1x1"){{
            localizedName = "Armor Panel";
            description = "A simple 1x1 armour plate. The backbone every machine is built on.";
        }});

        baseLong = add(new ModulBase("base1x2"){{
            localizedName = "Long Armor Panel";
            description = "A 2x1 armour plate covering more area at once.";
            w = 2; h = 1;
            weight = 1.8f;
            health = 300f;
        }});

        baseBig = add(new ModulBase("base2x2"){{
            localizedName = "Big Armor Panel";
            description = "A 2x2 armour plate covering more area at once.";
            w = 2; h = 2;
            weight = 3.4f;
            health = 600f;
        }});

        // ---- Root (control) ----
        root = add(new ModulRoot("main1x1"){{
            localizedName = "Command Core";
            description = "The brain of the machine. Every unit needs exactly one.";
            limit = 1;
            weaponSlots = 3;
            engineSlots = 2;
            abilitySlots = 1;
        }});
        rootMedium = add(new ModulRoot("main2x2"){{
            localizedName = "Medium Command Core";
            description = "The brain of the machine. Every unit needs exactly one.";
            limit = 1;
            w = 2; h = 2;
            weaponSlots = 6;
            engineSlots = 4;
            abilitySlots = 2;
        }});

        // ---- Engines (power) ----
        engine = add(new ModulEngine("engine1x1"){{
            localizedName = "Engine";
            description = "Compact generator producing power for the machine.";
            powerProduction = 3.1f;
        }});

        engineMedium = add(new ModulEngine("engine2x2"){{
            localizedName = "Medium Engine";
            description = "Heavy 2x2 engine. Produces a lot of power, but takes two engine slots.";
            slotCost = 2;
            w = 2; h = 2;
            weight = 6f;
            health = 260f;
            powerProduction = 14f;
        }});

        // ---- Movement ----
        wheel = add(new ModulWheel("wheel1x1"){{
            localizedName = "Wheel";
            description = "Drive wheel. Sets top speed and hauls weight, but needs power to spin.";
            moveSpeed = 2f;
            haulWeight = 3f;
            rotateSpeed = 4.5f;
            powerUse = 0.6f;
        }});

        track = add(new ModulWheel("track3x1"){{
            localizedName = "Track";
            description = "Heavy track. Lower speed, but can haul more weight.";
            moveSpeed = 1.8f;
            w = 1; h = 3;
            haulWeight = 9f;
            rotateSpeed = 3.5f;
            powerUse = 1f;
        }});

        trackBig = add(new ModulWheel("track6x1"){{
            localizedName = "Big Track";
            description = "Heavy track. Lower speed, but can haul more weight.";
            moveSpeed = 1.7f;
            w = 1; h = 6;
            haulWeight = 19f;
            rotateSpeed = 3f;
            powerUse = 2f;
        }});

        // ---- Weapons ----
        gun = add(new ModulTurret("gun1x1"){{
            localizedName = "Light Gun";
            description = "A rotating autocannon turret.";
            weapon = new Weapon("modularis-gun1x1"){{
                rotate = true;
                reload = 22f;
                inaccuracy = 3f;
                rotateSpeed = 8f;
                shootCone = 14f;
                shootSound = Sounds.shoot;
                bullet = new BasicBulletType(4f, 14f){{
                    lifetime = 38f;
                    width = 7f;
                    height = 9f;
                    shrinkY = 0.2f;
                    trailColor = Color.valueOf("ffd37f");
                    hitColor = trailColor;
                }};
            }};
        }});

        // ---- Abilities ----
        mender = add(new ModulMender("mender1x1"){{
            localizedName = "Mender";
            description = "Repair emitter. Periodically pulses to heal nearby allied units.";
            reload = 200f;
            healRange = 100f;
            healAmount = 55f;
            powerUse = 2f;
        }});

        turboHeater = add(new ModulTurbo("turboheater2x2"){{
            localizedName = "Turbo Heater";
            description = "Overdrives the drivetrain, doubling the machine's top speed. ";
            w = 2; h = 2;
            slotCost = 2;
            speedBoost = 2f;
            weight = 3f;
            health = 130f;
            powerUse = 2.5f;
        }});
    }

    private static <T extends ModuleType> T add(T type){
        all.add(type);
        nameMap.put(type.name, type);
        return type;
    }

    public static ModuleType byName(String name){
        return nameMap.get(name);
    }
}
