package modularis.content;

import arc.graphics.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.weapons.*;
import modularis.type.units.modules.*;

public class MdlModules{
    /** All registered modules, in registration order. */
    public static final Seq<ModuleType> all = new Seq<>();
    /** Fast name -> module lookup, used by design (de)serialization. */
    public static final ObjectMap<String, ModuleType> nameMap = new ObjectMap<>();

    public static ModuleType
        // base / armour
        basePanel, baseLong, baseLong2, baseBig, baseLoong, baseLoong2, baseGigant,
        // control
        root, rootMedium, rootBig, transmission, gunbridge,
        // power
        engine, engineMedium, engineBig,
        // movement
        wheel, track, trackBig, trackGigant,
        // weapons
        gun, discharger, cannon, laculum, artillery, wolfRae, pointDefence,
        // abilities
        mender, pulsus, turboHeater, compressor, reactiveArmorer, transformator, c4, shieldEmitter;

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

        baseGigant = add(new ModulBase("base4x4"){{
            localizedName = "Gigant Armor Panel";
            description = "A 4x4 armour plate covering more area at once.";
            w = 4; h = 4;
            weight = 6.8f;
            health = 1200f;
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
            weight = 2f;
            weaponSlots = 6;
            engineSlots = 4;
            abilitySlots = 2;
        }});
        rootBig = add(new ModulRoot("main4x4"){{
            localizedName = "Big Command Core";
            description = "The brain of the machine. Every unit needs exactly one.";
            limit = 1;
            w = 4; h = 4;
            weight = 7f;
            weaponSlots = 24;
            engineSlots = 10;
            abilitySlots = 5;
        }});
        transmission = add(new ModulRoot("transmission1x3"){{
            localizedName = "Transmission";
            description = "Increases the maximum number of engines possible.";
            limit = 2;
            w = 3; h = 1;
            weaponSlots = 0;
            engineSlots = 3;
            abilitySlots = 0;
            powerUse = 2f;
            slot = SlotType.ability;
            slotCost = 1;
        }});

        gunbridge = add(new ModulRoot("gunbridge2x2"){{
            localizedName = "Gun Bridge";
            description = "Increases the maximum number of weapons possible.";
            limit = 3;
            w = 2; h = 2;
            weaponSlots = 6;
            engineSlots = 0;
            abilitySlots = 0;
            powerUse = 2f;
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

        engineBig = add(new ModulEngine("engine4x4"){{
            localizedName = "Big Engine";
            description = "Produces a lot of power.";
            slotCost = 4;
            w = 4; h = 4;
            weight = 18f;
            health = 600f;
            powerProduction = 34f;
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

        trackGigant = add(new ModulWheel("track12x2"){{
            localizedName = "Gigant Track";
            description = "Heavy track. Lower speed, but can haul more weight.";
            moveSpeed = 1.7f;
            w = 2; h = 12;
            haulWeight = 42f;
            rotateSpeed = 3f;
            powerUse = 7f;
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

        laculum = add(new ModulTurret("laculum2x2"){{
            localizedName = "Laculum";
            description = "Heavy rocket fire. Rockets cause corrosion.";
            baseSprite = "base2x2";
            weight = 7f;
            health = 300f;
            w = 2; h = 2;
            powerUse = 3f;
            slotCost = 2;
            weapon = new Weapon("modularis-laculum2x2"){{
                rotate = true;
                reload = 300f;
                inaccuracy = 4f;
                rotateSpeed = 5f;
                recoil = 0.5f;
                shootCone = 12f;
                shoot = new ShootBarrel(){{
                    barrels = new float[]{
                        -2, -1.25f, 0,
                        0, 0, 0,
                        2, -1.25f, 0
                    };
                    shots = 12;
                    shotDelay = 5f;
                }};
                shootSound = Sounds.shootMissile;
                bullet = new MissileBulletType(4f, 10){{
                    width = 8f;
                    height = 8f;
                    lifetime = 60f;
                    splashDamageRadius = 30f;
                    splashDamage = 30f * 1.5f;
                    trailLength = 3;
                    trailWidth = 0.5f;
                    hitEffect = Fx.blastExplosion;
                    despawnEffect = Fx.blastExplosion;

                    status = StatusEffects.corroded;

                    hitColor = backColor = trailColor = Pal.heal;
                    frontColor = Pal.heal;
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
                rotate = true;
                reload = 300f;
                inaccuracy = 2f;
                rotateSpeed = 1f;
                shootCone = 5f;
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

        wolfRae = add(new ModulTurret("wolf-rae4x4"){{
            localizedName = "Wolf Rae";
            description = "A massive, deadly laser turret.";
            baseSprite = "base4x4";
            weight = 40f;
            health = 800f;
            w = 4; h = 4;
            powerUse = 30f;
            slotCost = 6;
            weapon = new Weapon("modularis-wolf-rae4x4"){{
                shake = 6f;

                shoot.firstShotDelay = MdlFX.neoplasmLaserChargeSmall.lifetime - 1f;
                parentizeEffects = true;


                rotate = true;
                rotateSpeed = 1f;
                reload = 700f;
                recoil = 1f;
                chargeSound = Sounds.chargeVela;
                shootSound = Sounds.beamPlasma;
                initialShootSound = Sounds.shootBeamPlasma;
                continuous = true;
                cooldownTime = 300f;

                bullet = new ContinuousLaserBulletType(){{
                    damage = 50f;
                    length = 200f;
                    hitEffect = Fx.hitMeltdown;
                    drawSize = 480f;
                    lifetime = 230f;
                    shake = 3f;
                    despawnEffect = Fx.smokeCloud;
                    smokeEffect = Fx.none;

                    chargeEffect = MdlFX.neoplasmLaserChargeSmall;

                    incendChance = 0.1f;
                    incendSpread = 5f;
                    incendAmount = 1;

                    colors = new Color[]{Pal.neoplasm1.cpy().a(.2f), Pal.neoplasm1.cpy().a(.5f), Pal.neoplasm2.cpy().mul(1.2f), Color.white};
                }};
            }};
        }});

        pointDefence = add(new ModulTurret("point-defence1x2"){{
            localizedName = "Point defence";
            description = "Provides protection against projectiles.";
            baseSprite = "base1x2";
            weight = 3f;
            health = 140f;
            w = 2; h = 1;
            powerUse = 2f;
            weapon = new PointDefenseWeapon("modularis-point-defence1x2"){{
                rotate = true;
                reload = 19f;
                targetInterval = 0f;
                targetSwitchInterval = 0f;
                shootSound = Sounds.shootLaser;
                bullet = new BulletType(){{
                    smokeEffect = Fx.pointHit;
                    hitEffect = Fx.pointHit;
                    maxRange = 120f;
                    damage = 9f;
                    speed = 3f;
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
            slotCost = 2;
            pulseColor = Color.valueOf("3ce1ff");
        }});

        shieldEmitter = add(new ModulPulsar("shield-emitter3x1"){{
            localizedName = "Shield Emitter";
            description = "Projects a force field around the machine, absorbing incoming fire "
                + "until it breaks.";
            w = 1; h = 3;
            weight = 3f;
            health = 150f;
            powerUse = 5f;
            slotCost = 2;
            hasPulseEffect = false;

            shieldRadius = 60f;
            shieldRegen = 0.4f;
            shieldMax = 500f;
            shieldCooldown = 60f * 6f;
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

        transformator = add(new ModuleType("transformator2x1"){{
            localizedName = "Transformator";
            description = "Increases the machine's power use and damage.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 1;
            w = 1; h = 2;
            powerUseMultiplier = 1.4f;
            damageMultiplier = 1.3f;
            weight = 1.5f;
            health = 120f;
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
