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
        basePanel, baseLong, baseLong2, baseBig, baseLoong, baseLoong2, baseGigant, cargo,
        // control
        root, rootMedium, rootBig, transmission, gunbridge, sandboxRoot,
        // power
        engine, engineMedium, engineBig,
        // movement
        wheel, track, trackBig, trackGigant, hover,
        // weapons
        gun, discharger, cannon, sanguis, laculum, flamethrower, artillery, pierceCannon, wolfRae, 
        pointDefence, buildTower, repairTower, airborne,
        // abilities
        mender, pulsus, turboHeater, overclocker, compressor, reactiveArmorer, transformator, reformator,c4, shieldEmitter, drill;

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
            armor = 0.5f;
        }});

        baseLong2 = add(new ModulBase("base2x1"){{
            localizedName = "Long Armor Panel";
            description = "A 1x2 armour plate covering more area at once.";
            w = 1; h = 2;
            weight = 1.8f;
            health = 300f;
            armor = 0.5f;
        }});

        baseBig = add(new ModulBase("base2x2"){{
            localizedName = "Big Armor Panel";
            description = "A 2x2 armour plate covering more area at once.";
            w = 2; h = 2;
            weight = 3.4f;
            health = 600f;
            armor = 1f;
        }});

        baseLoong = add(new ModulBase("base1x3"){{
            localizedName = "Long Armor Panel";
            description = "A 3x1 armour plate covering more area at once.";
            w = 3; h = 1;
            weight = 2.7f;
            health = 450f;
            armor = 0.8f;
        }});

        baseLoong2 = add(new ModulBase("base3x1"){{
            localizedName = "Long Armor Panel";
            description = "A 1x3 armour plate covering more area at once.";
            w = 1; h = 3;
            weight = 2.7f;
            health = 450f;
            armor = 0.8f;
        }});

        baseGigant = add(new ModulBase("base4x4"){{
            localizedName = "Gigant Armor Panel";
            description = "A 4x4 armour plate covering more area at once.";
            w = 4; h = 4;
            weight = 6.8f;
            health = 1200f;
            armor = 4f;
        }});

        cargo = add(new ModuleType("cargo1x2"){{
            localizedName = "Cargo ";
            description = "Can store items.";
            w = 2; h = 1;
            cargoCapacity = 50;
            weight = 1.4f;
        }});

        // ---- Root (control) ----
        root = add(new ModulRoot("main1x1"){{
            localizedName = "Command Core";
            description = "The brain of the machine. Every unit needs exactly one.";
            limit = 1;
            weaponSlots = 3;
            engineSlots = 2;
            abilitySlots = 1;
            armor = 1f;
            weight = 1f;
            cargoCapacity = 10;
        }});
        rootMedium = add(new ModulRoot("main2x2"){{
            localizedName = "Medium Command Core";
            description = "The brain of the machine. Every unit needs exactly one.";
            limit = 1;
            w = 2; h = 2;
            weight = 3f;
            weaponSlots = 6;
            engineSlots = 4;
            abilitySlots = 2;
            armor = 3f;
            cargoCapacity = 20;
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
            armor = 8f;
            cargoCapacity = 40;
        }});
        transmission = add(new ModulRoot("transmission1x3"){{
            localizedName = "Transmission";
            description = "Increases the maximum number of engines possible.";
            limit = 4;
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
            limit = 5;
            w = 2; h = 2;
            weaponSlots = 6;
            engineSlots = 0;
            abilitySlots = 0;
            powerUse = 2f;
            slot = SlotType.ability;
            slotCost = 1;
        }});

        sandboxRoot = add(new ModulRoot("sandbox-main1x1"){{
            localizedName = "Sandbox Command Core";
            description = "Unlimited slots.";
            weaponSlots = 9999;
            engineSlots = 9999;
            abilitySlots = 9999;
            armor = 1f;
            weight = 1f;
            cargoCapacity = 10;
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

        hover = add(new ModulHover("hover6x1"){{
            localizedName = "Hover";
            description = "Allows hovering over the ground, but has a weight limit.";
            moveSpeed = 2.2f;
            w = 1; h = 6;
            haulWeight = 20f;
            maxWeight = 150f;
            rotateSpeed = 5f;
            powerUse = 2.1f;
        }});

        // ---- Weapons ----
        gun = add(new ModulTurret("gun1x1"){{
            localizedName = "Light Gun";
            description = "A rotating autocannon turret.";
            weapon = new Weapon("modularis-gun1x1"){{
                rotate = true;
                reload = 19f;
                inaccuracy = 3f;
                rotateSpeed = 8f;
                shootCone = 14f;
                shootSound = Sounds.shoot;
                bullet = new BasicBulletType(4f, 19f){{
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
                    damage = 14;
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
                    splashDamage = 40f;
                    splashDamageRadius = 40f;
                }};
            }};
        }});

        sanguis = add(new ModulTurret("sanguis2x1"){{
            localizedName = "Sanguis";
            description = "Steals health from enemies to repair the vehicle.";
            baseSprite = "base2x1";
            weight = 3.1f;
            health = 120f;
            w = 1; h = 2;
            powerUse = 2.4f;
            slotCost = 1;
            weapon = new Weapon("modularis-sanguis2x1"){{
                rotate = true;
                reload = 19f;
                inaccuracy = 1f;
                rotateSpeed = 8f;
                shootCone = 2f;
                shootSound = Sounds.shootSap;

                bullet = new SapBulletType(){{
                    sapStrength = 0.8f;
                    length = 40f;
                    damage = 18;
                    shootEffect = Fx.shootSmall;
                    hitColor = color = Color.valueOf("e95eb4");
                    despawnEffect = Fx.none;
                    width = 0.4f;
                    lifetime = 17f;
                    knockback = -0.65f;
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
                    splashDamage = 30f;
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

        flamethrower = add(new ModulTurret("flamethower3x1"){{
            localizedName = "Flamethrower";
            description = "Accendite eos igne Graeco.";
            baseSprite = "base3x1";
            weight = 3.8f;
            health = 180f;
            w = 1; h = 3;
            powerUse = 2.7f;
            slotCost = 2;
            weapon = new Weapon("modularis-flamethower3x1"){{
                rotate = true;
                reload = 6f;
                inaccuracy = 1f;
                rotateSpeed = 8f;
                shootCone = 2f;
                shootSound = Sounds.shootFlame;
                recoil = 0.2f;
                ejectEffect = Fx.none;
                bullet = new BulletType(4.2f, 17f){{
                    hitSize = 12f;
                    lifetime = 13f;
                    statusDuration = 60f * 7;
                    shootEffect = MdlFX.shootFlame;
                    hitEffect = Fx.hitFlameSmall;
                    despawnEffect = Fx.none;
                    status = StatusEffects.burning;
                    keepVelocity = false;
                    hittable = false;
                }};
            }};
        }});

        artillery = add(new ModulTurret("artillery3x2"){{
            localizedName = "Artillery";
            description = "Heavy long-range artillery.";
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
                    splashDamageRadius = 70f;
                }};
            }};
        }});

        pierceCannon = add(new ModulTurret("pierce-cannon3x2"){{
            localizedName = "Pierce Cannon";
            description = "A massive tank gun that pierces through enemies.";
            baseSprite = "base3x2";
            weight = 15f;
            health = 500f;
            w = 2; h = 3;
            powerUse = 5f;
            slotCost = 4;
            weapon = new Weapon("modularis-pierce-cannon3x2"){{
                rotate = true;
                reload = 180f;
                inaccuracy = 1f;
                rotateSpeed = 3f;
                shootCone = 6f;
                ejectEffect = Fx.casing3;
                shootSound = Sounds.explosionDull;

                bullet = new BasicBulletType(6f, 180){{
                    sprite = "missile-large";
                    width = 7.5f;
                    height = 13f;
                    lifetime = 32f;
                    hitSize = 6f;
                    pierceCap = 3;
                    pierce = true;
                    pierceBuilding = true;
                    hitColor = backColor = trailColor = Color.valueOf("f68630");
                    frontColor = Color.white;
                    trailWidth = 2.8f;
                    trailLength = 5;
                    hitEffect = despawnEffect = Fx.blastExplosion;
                    shootEffect = Fx.shootTitan;
                    smokeEffect = Fx.shootSmokeTitan;
                    splashDamageRadius = 10f;
                    splashDamage = 170f;

                    fragBullets = 3;

                    fragBullet = new BasicBulletType(7f, 34){{
                        sprite = "missile-large";
                        width = 4f;
                        height = 6f;
                        lifetime = 6f;
                        hitSize = 4f;
                        hitColor = backColor = trailColor = Color.valueOf("feb380");
                        frontColor = Color.white;
                        trailWidth = 1.7f;
                        trailLength = 3;
                        drag = 0.01f;
                        despawnEffect = hitEffect = Fx.hitBulletColor;
                        fragBullets = 3;

                        fragBullet = new BasicBulletType(4f, 22){{
                            sprite = "missile-large";
                            width = 3f;
                            height = 5f;
                            lifetime = 6f;
                            hitSize = 3f;
                            hitColor = backColor = trailColor = Color.valueOf("feb380");
                            frontColor = Color.white;
                            trailWidth = 1.7f;
                            trailLength = 2;
                            drag = 0.01f;
                            despawnEffect = hitEffect = Fx.hitBulletColor;
                        }};
                    }};
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
                    damage = 80f;
                    length = 200f;
                    hitEffect = Fx.hitMeltdown;
                    drawSize = 480f;
                    lifetime = 230f;
                    shake = 3f;
                    despawnEffect = Fx.smokeCloud;
                    smokeEffect = Fx.none;
                    status = StatusEffects.melting;

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

        buildTower = add(new ModulTurret("build-tower1x2"){{
            localizedName = "Build Tower";
            description = "Can build blocks.";
            baseSprite = "base1x2";
            weight = 3f;
            health = 140f;
            w = 2; h = 1;
            powerUse = 3f;
            buildSpeed = 1f;
            weapon = new BuildWeapon("modularis-build-tower1x2"){{
                rotate = true;
                rotateSpeed = 7f;
            }};
        }});

        repairTower = add(new ModulTurret("repair-tower2x1"){{
            localizedName = "Repair Tower";
            description = "Repair ally buildings.";
            baseSprite = "base2x1";
            weight = 3f;
            health = 140f;
            w = 1; h = 2;
            powerUse = 2.1f;
            weapon = new RepairBeamWeapon("modularis-repair-tower2x1"){{
                widthSinMag = 0.11f;
                reload = 20f;
                rotate = false;
                beamWidth = 0.7f;
                shootCone = 15f;
                mirror = false;
                rotate = true;

                repairSpeed = 3.3f;
                fractionRepairSpeed = 0.06f;

                targetUnits = false;
                targetBuildings = true;
                autoTarget = false;
                controllable = true;
                laserColor = Pal.accent;
                healColor = Pal.accent;

                bullet = new BulletType(){{
                    maxRange = 60f;
                }};
            }};
        }});

        airborne = add(new ModulTurret("airborne4x4"){{
            localizedName = "Airborne";
            description = "Shoot a capsule containing 8 daggers.";
            baseSprite = "base4x4";
            weight = 40f;
            health = 1200f;
            w = 4; h = 4;
            powerUse = 28f;
            slotCost = 7;
            weapon = new Weapon("modularis-airborne4x4"){{
                rotate = true;
                reload = 2000f;
                inaccuracy = 2f;
                rotateSpeed = 4f;
                shootCone = 1f;
                shootSound = Sounds.shootMissileLarge;
                bullet = new ArtilleryBulletType(4f, 20f){{
                    lifetime = 150f;

                    sprite = "missile-large";
                    width = 19f;
                    height = 26f;
                    collidesAir = false;
                    collidesGround = false;

                    splashDamage = 100f;
                    splashDamageRadius = 50f;

                    trailWidth = 6f;
                    trailLength = 8;

                    fragBullets = 8;

                    fragBullet = new BasicBulletType(4f, 6){{
                        sprite = "missile-large";
                        width = 3f;
                        height = 5f;
                        lifetime = 6f;
                        trailWidth = 1.7f;
                        trailLength = 2;
                        spawnUnit = UnitTypes.dagger;
                        drag = 0.01f;
                    }};
                }};
            }};
        }});

        // ---- Abilities ----
        mender = add(new ModulPulsar("mender1x1"){{
            localizedName = "Mender";
            description = "Repair emitter. Periodically pulses to heal nearby allied units.";
            reload = 200f;
            pulseRange = 100f;
            healAmount = 100f;
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

        drill = add(new ModulDrill("drill2x3"){{
            localizedName = "Drill";
            description = "Grinds ore the machine drives over straight into its hold. "
                + "Also lets the machine be ordered to mine, hauling ore back to the core.";
            w = 3; h = 2;
            weight = 4f;
            health = 200f;
            powerUse = 4f;
            slotCost = 1;

            tier = 4;
            drillSpeed = 1f;
            drillTime = 45f;
            mineRange = 110f; 
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

        reformator = add(new ModuleType("reformator2x2"){{
            localizedName = "Reformator";
            description = "Changes many of the machine's stats.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 1;
            w = 2; h = 2;
            reloadMultiplier = 0.8f;
            damageMultiplier = 1.2f;
            healthMultiplier = 0.8f;
            speedMultiplier = 1.2f;
            weight = 3f;
            health = 200f;
            powerUse = 3f;
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

        overclocker = add(new ModuleType("overclocker2x2"){{
            localizedName = "Overclocker";
            description = "Overdrives the turrets fire rate.";
            category = ModuleCategory.ability;
            slot = SlotType.ability;
            slotCost = 2;
            w = 2; h = 2;
            reloadMultiplier = 1.3f;
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
