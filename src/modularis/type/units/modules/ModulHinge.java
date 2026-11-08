package modularis.type.units.modules;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;

import modularis.content.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

/** A turret ring that carries a cannon built in its own design ({@link PlacedModule#cannon}). */
public class ModulHinge extends ModuleType{
    public float radius = 3f;
    public float weightLimit = 6f;
    public float rotateSpeed = 9f;
    public boolean unlimited = false;

    public static @Nullable ModularDesign previewDesign;

    public ModulHinge(String name){
        super(name);
        category = ModuleCategory.weapon;
        slot = SlotType.weapon;
        shedResistance = 0.6f;
    }

    public void createMounts(PlacedModule placed, float localX, float localY, Seq<WeaponMount> out){
        if(placed.cannon == null || placed.cannon.isEmpty()) return;

        CannonSpec spec = CannonSpec.compute(placed.cannon, this);
        float cell = ModularUnitType.cellWorld();
        int ammo = spec.ammo();
        boolean first = true;

        IntSeq groups = new IntSeq();
        for(CannonSpec.Line line : spec.lines) if(!groups.contains(line.group())) groups.add(line.group());

        for(int g = 0; g < groups.size; g++){
            int group = groups.get(g);
            Seq<CannonSpec.Line> lines = spec.lines.select(l -> l.group() == group);
            CannonSpec.Line line = lines.first();
            CannonCaliber caliber = CannonCaliber.get(line.caliber);

            CannonWeapon weapon = new CannonWeapon(placed, this);
            weapon.drawsCannon = first;
            weapon.x = localX;
            weapon.y = localY;
            //each line keeps its own reload; taking turns, the group fires that many times as often
            weapon.reload = spec.reload(line) / lines.size;
            weapon.rotateSpeed = spec.rotateSpeed;
            weapon.inaccuracy = spec.inaccuracy(line);
            weapon.shootCone = 8f;
            weapon.recoil = caliber.recoil;
            weapon.shake = caliber.shake;
            weapon.shootSound = caliber.sound;
            weapon.bullet = caliber.bullets[ammo];
            weapon.damageScale = spec.damageScale(line);
            weapon.speedScale = spec.speedScale(line);
            weapon.rangeScale = spec.rangeScale();
            weapon.shoot = barrelsFor(lines, cell);
            weapon.init();
            if(weapon.recoilTime < 0f) weapon.recoilTime = weapon.reload;
            if(!headless) weapon.load();

            out.add(weapon.mountType.get(weapon));
            first = false;
        }
    }

    /** One shot per trigger from each muzzle in turn. */
    public static ShootBarrel barrelsFor(Seq<CannonSpec.Line> lines, float cell){
        float[] muzzles = new float[lines.size * 3];
        for(int i = 0; i < lines.size; i++){
            muzzles[i * 3] = lines.get(i).muzzleX * cell;
            muzzles[i * 3 + 1] = lines.get(i).muzzleY * cell;
        }

        //not set in an initializer block: there "barrels" would be the inherited field, assigned to itself
        ShootBarrel pattern = new ShootBarrel();
        pattern.barrels = muzzles;
        pattern.shots = 1;
        return pattern;
    }

    /** Longest reach of any line on this cannon, 0 if it can't fire. */
    public float range(PlacedModule placed){
        if(placed.cannon == null) return 0f;
        float range = 0f;
        CannonSpec spec = CannonSpec.compute(placed.cannon, this);
        for(CannonSpec.Line line : spec.lines){
            range = Math.max(range, spec.range(line));
        }
        return range;
    }

    @Override
    public void drawTop(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        if(placed.cannon == null || placed.cannon.isEmpty()) return;

        boolean dead = false;
        Color tint = Color.white;
        if(unit instanceof ModularUnitEntity e && e.design != null){
            if(e.hasCannonMount(placed)) return;
            dead = !e.design.isActive(placed);
            tint = e.design.colorOf(placed);
        }else if(previewDesign != null){
            tint = previewDesign.colorOf(placed);
        }

        drawCannon(placed.cannon, x, y, rotation, w / this.w, tint, unit != null, dead);
    }

    public static Color partColor(ModularDesign cannon, PlacedModule part, Color inherited){
        if(part.color != null) return part.color;
        return cannon.color.equals(Color.white) ? inherited : cannon.color;
    }

    public static void drawCannon(ModularDesign cannon, float x, float y, float rotation, float cell, Color tint, boolean world, boolean dead){
        float z = Draw.z();
        float outline = world ? cell / ModularUnitType.spritePx * MdlUnits.modularUnit.outlineRadius : Math.max(cell * 0.19f, 1f);
        float base = Layer.groundUnit + 0.016f;

        if(world) Draw.z(base);
        Draw.color(MdlUnits.modularUnit.outlineColor);
        for(PlacedModule m : cannon.modules){
            float px = x + Angles.trnsx(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
            float py = y + Angles.trnsy(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
            for(int i = 0; i < 4; i++){
                Draw.rect(m.type.region(), px + Angles.trnsx(i * 90f, outline), py + Angles.trnsy(i * 90f, outline),
                    m.type.w * cell, m.type.h * cell, rotation);
            }
        }

        if(world) Draw.z(base + 0.001f);
        Draw.color(dead ? Color.gray : Color.white);
        for(PlacedModule m : cannon.modules){
            float px = x + Angles.trnsx(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
            float py = y + Angles.trnsy(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
            Draw.rect(m.type.region(), px, py, m.type.w * cell, m.type.h * cell, rotation);
        }

        if(!dead){
            for(int tone = 0; tone < 3; tone++){
                if(world) Draw.z(base + 0.0012f + tone * 0.0002f);
                for(PlacedModule m : cannon.modules){
                    Color paint = partColor(cannon, m, tint);
                    if(paint.equals(Color.white)) continue;

                    TextureRegion region = m.type.paintRegion(tone);
                    if(region == null || !region.found()) continue;

                    Draw.color(PaintRamp.of(paint).tone(tone));
                    float px = x + Angles.trnsx(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
                    float py = y + Angles.trnsy(rotation, (m.x + m.type.w / 2f - cannon.pivot) * cell, (m.y + m.type.h / 2f - cannon.pivot) * cell);
                    Draw.rect(region, px, py, m.type.w * cell, m.type.h * cell, rotation);
                }
            }
        }

        Draw.color();
        if(world) Draw.z(z);
    }

    @Override
    public void buildStats(Table table){
        if(unlimited){
            stat(table, "Cannon size", "[accent]Unlimited[]");
            stat(table, "Traverse", Strings.autoFixed(rotateSpeed * 60f, 0) + " deg/s, whatever it weighs");
        }else{
            stat(table, "Cannon radius", Strings.autoFixed(radius, 0) + " cells");
            stat(table, "Cannon weight limit", Strings.autoFixed(weightLimit, 0));
            stat(table, "Traverse", Strings.autoFixed(rotateSpeed * 60f, 0) + " deg/s, less when heavy");
        }
        stat(table, "Build", "[accent]Place it, then press its Cannon button[]");
    }
}
