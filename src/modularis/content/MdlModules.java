package modularis.content;

import arc.graphics.*;
import arc.struct.*;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
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
        basePanel, baseLong, baseLong2, baseBig, baseLoong, baseLoong2,
        // control
        root, rootMedium, transmission,
        // power
        engine, engineMedium,
        // movement
        wheel, track, trackBig,
        // weapons
        gun, discharger, cannon, artillery,
        // abilities
        mender, pulsus, turboHeater, compressor, reactiveArmorer, c4;

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

        baseLong2 = add(new ModulBase("base2x1"){{
            localizedName = "Long Armor Panel";
            description = "A 1x2 armour plate covering more area at once.";
            w = 1; h = 2;
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

        baseLoong = add(new ModulBase("base1x3"){{
            localizedName = "Long Armor Panel";
            description = "A 3x1 armour plate covering more area at once.";
            w = 3; h = 1;
            weight = 2.7f;
            health = 450f;
        }});

        baseLoong2 = add(new ModulBase("base3x1"){{
            localizedName = "Long Armor Panel";
            description = "A 1x3 armour plate covering more area at once.";
            w = 1; h = 3;
            weight = 2.7f;
            health = 450f;
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
        transmission = add(new ModulRoot("transmission1x3"){{
            localizedName = "Transmission";
            description = "Increases the maximum number of engines possible.";
            limit = 1;
            w = 3; h = 1;
            weaponSlots = 0;
            engineSlots = 3;
            abilitySlots = 0;
            slot = SlotType.ability;
            slotCost = 1;
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

        discharger = add(new ModulTurret("discharger1x1"){{
            localizedName = "Discharger";
            description = "Emits bursts of energy.";
            powerUse = 1.6f;
            weapon = new Weapon("modularis-discharger1x1"){{
                rotate = true;
                reload = 13f;
                inaccuracy = 5f;
                rotateSpeed = 8f;
                shootCone = 23f;
                shootSound = Sounds.shootArc;
                bullet = new LightningBulletType(){{
                    damage = 12;
                    lightningLength = 19;
                    collidesAir = true;
    
                    lightningType = new BulletType(0.0001f, 0f){{
                        lifetime = Fx.lightning.lifetime;
                        hitEffect = Fx.hitLancer;
                        despawnEffect = Fx.none;
                        status = StatusEffects.shocked;
                        hittable = false;
                        lightColor = Color.gold;
                        collidesAir = false;
                    }};
                }};
            }};
        }});

        cannon = add(new ModulTurret("cannon2x1"){{
            localizedName = "Cannon";
            description = "Has a lot of damage, but slow.";
            baseSprite = "base2x1";
            weight = 3f;
            health = 140f;
            w = 1; h = 2;
            powerUse = 1.4f;
            slotCost = 2;
            weapon = new Weapon("modularis-cannon2x1"){{
                rotate = true;
                reload = 56f;
                inaccuracy = 2f;
                rotateSpeed = 7f;
                shootCone = 5f;
                ejectEffect = Fx.casing1;
                shootSound = Sounds.shootArtillery;
                bullet = new ArtilleryBulletType(3f, 30f){{
                    lifetime = 65f;
                    width = 10f;
                    height = 12f;
                    splashDamage = 50f;
                    splashDamageRadius = 50f;
                }};
            }};
        }});

        artillery = add(new ModulTurret("artillery3x2"){{
            localizedName = "Cannon";
            description = "Has a lot of damage, but slow.";
            baseSprite = "base3x2";
            weight = 14f;
            health = 400f;
            w = 2; h = 3;
            powerUse = 6f;
            slotCost = 4;
            weapon = new Weapon("modularis-artillery3x2"){{
                rotate = false;
                reload = 300f;
                inaccuracy = 2f;
                rotateSpeed = 0f;
                shootCone = 5f;
                shake = 4f;
                ejectEffect = Fx.casing2;
                shootSound = Sounds.explosionTitan;
                bullet = new ArtilleryBulletType(4f, 350f){{
                    lifetime = 150f;
                    width = 18f;
                    height = 20f;
                    splashDamage = 400f;
                    splashDamageRadius = 100f;
                }};
            }};
        }});

        // ---- Abilities ----
        mender = add(new ModulPulsar("mender1x1"){{
            localizedName = "Mender";
            description = "Repair emitter. Periodically pulses to heal nearby allied units.";
            reload = 200f;
            pulseRange = 100f;
            healAmount = 55f;
            powerUse = 2f;
        }});

        pulsus = add(new ModulPulsar("pulsus3x1"){{
            localizedName = "Pulsus";
            description = "Damages nearby enemies and can rip a "
                + "module clean off their machines.";
            reload = 320f;
            pulseRange = 85f;
            w = 1; h = 3;
            damage = 45f;
            tearChance = 0.3f;
            powerUse = 4f;
            pulseColor = Color.valueOf("3ce1ff");
        }});

        compressor = add(new ModuleType("compressor1x1"){{
            localizedName = "Compressor";
            description = "Compresses the machine's systems: less weight, less health.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 1;
            weightMultiplier = 0.8f;
            healthMultiplier = 0.7f;
            weight = 1.5f;
            health = 20f;
            powerUse = 1f;
        }});

        reactiveArmorer = add(new ModuleType("reactive-armorer1x1"){{
            localizedName = "Reactive Armorer";
            description = "Improves protection, but makes machine heavier.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 1;
            weightMultiplier = 1.3f;
            healthMultiplier = 1.2f;
            weight = 1.5f;
            health = 20f;
            powerUse = 1f;
        }});

        c4 = add(new ModulC4("c41x1"){{
            localizedName = "C4 Charge";
            description = "Turns the machine into a kamikaze: it charges the nearest enemy "
                + "and detonates. More charges, bigger blast.";
            slotCost = 1;
            damage = 700f;
            radius = 50f;
            weight = 2f;
            health = 40f;
            healthMultiplier = 0.5f;
        }});

        turboHeater = add(new ModuleType("turboheater2x2"){{
            localizedName = "Turbo Heater";
            description = "Overdrives the drivetrain, doubling the machine's top speed.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 2;
            w = 2; h = 2;
            speedMultiplier = 2f;
            weight = 3f;
            health = 130f;
            powerUse = 2.5f;

            ambientEffect = MdlFX.turboSmoke;
            ambientChance = 0.1f;
            ambientColor = Color.valueOf("9a8f86");
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
