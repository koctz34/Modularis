package modularis.type.units;

import arc.math.*;
import arc.struct.*;
import arc.util.*;

import modularis.type.units.modules.*;

public class CannonSpec{
    public static class Line{
        public int caliber, length;
        public float muzzleX, muzzleY;

        public int group(){
            return caliber * 1000 + length;
        }
    }

    public static final float cellPivot = 0.5f;
    public static final float cornerPivot = 0f;

    public final Seq<Line> lines = new Seq<>();
    public float weight, health, rotateSpeed, pivot;
    /** Extra range each sight gives, and the most sights can reach together. */
    public static final float sightRange = 0.2f, maxRangeScale = 1.8f;

    public int autoloaders, stabilizers, motors, sights, he, ap, deadBarrels, dryBreeches;

    public int ammo(){
        return he > ap ? CannonCaliber.he : ap > he ? CannonCaliber.ap : CannonCaliber.standard;
    }

    public static CannonSpec compute(ModularDesign cannon, ModulHinge hinge){
        CannonSpec s = new CannonSpec();
        s.weight = cannon.totalWeight();
        s.health = cannon.totalHealth();
        s.pivot = cannon.pivot;
        float pivot = cannon.pivot;

        ObjectSet<PlacedModule> used = new ObjectSet<>();
        int barrels = 0;

        for(PlacedModule m : cannon.modules){
            if(!(m.type instanceof CannonPart part)) continue;
            switch(part.kind){
                case barrel -> barrels++;
                case autoloader -> s.autoloaders++;
                case stabilizer -> s.stabilizers++;
                case motor -> s.motors++;
                case ammoHE -> s.he++;
                case ammoAP -> s.ap++;
                case sight -> s.sights++;
                default -> {}
            }
            if(part.kind != CannonPart.Kind.breech) continue;

            int length = 0, y = m.y + part.h;
            while(true){
                PlacedModule next = cannon.get(m.x, y);
                if(next == null || next.x != m.x || next.y != y || !(next.type instanceof CannonPart b)
                    || b.kind != CannonPart.Kind.barrel || b.caliber != part.caliber) break;
                used.add(next);
                length++;
                y += b.h;
            }

            if(length == 0){
                s.dryBreeches++;
                continue;
            }

            Line line = new Line();
            line.caliber = part.caliber;
            line.length = length;
            line.muzzleX = m.x + part.w / 2f - pivot;
            line.muzzleY = y - pivot;
            s.lines.add(line);
        }
        s.deadBarrels = barrels - used.size;

        float load = hinge.weightLimit <= 0f ? 1f : s.weight / hinge.weightLimit;
        s.rotateSpeed = hinge.rotateSpeed * Mathf.clamp(1.35f - load, 0.35f, 1.2f) * (1f + 0.3f * s.motors);
        return s;
    }

    private float barrelShare(Line line){
        CannonCaliber c = CannonCaliber.get(line.caliber);
        return Math.min(line.length, c.optimalLength) / (float)c.optimalLength;
    }

    public float damageScale(Line line){
        return 0.6f + 0.4f * barrelShare(line);
    }

    public float speedScale(Line line){
        return 0.7f + 0.3f * barrelShare(line);
    }

    public float inaccuracy(Line line){
        return CannonCaliber.get(line.caliber).inaccuracy * (1.6f - 0.6f * barrelShare(line)) / (1f + 0.5f * stabilizers);
    }

    public float reload(Line line){
        return CannonCaliber.get(line.caliber).reload * Math.max(0.5f, 1f - 0.2f * autoloaders);
    }

    public float rangeScale(){
        return Math.min(1f + sightRange * sights, maxRangeScale);
    }

    public float range(Line line){
        return CannonCaliber.get(line.caliber).range * rangeScale();
    }

    public float damage(Line line){
        return CannonCaliber.get(line.caliber).bullets[ammo()].damage * damageScale(line);
    }

    public static boolean inRadius(ModuleType type, int x, int y, float radius, float pivot){
        for(int cx = x; cx < x + type.w; cx++){
            for(int cy = y; cy < y + type.h; cy++){
                if(Mathf.len(cx + 0.5f - pivot, cy + 0.5f - pivot) > radius) return false;
            }
        }
        return true;
    }

    public static boolean canAdd(ModularDesign cannon, ModulHinge hinge, ModuleType type){
        return cannon.canAdd(type) && cannon.totalWeight() + type.weight <= hinge.weightLimit + 0.0001f;
    }

    public static boolean canPlace(ModularDesign cannon, ModulHinge hinge, ModuleType type, int x, int y){
        return canAdd(cannon, hinge, type) && cannon.canPlace(type, x, y) && inRadius(type, x, y, hinge.radius, cannon.pivot);
    }

    public String summary(ModulHinge hinge){
        StringBuilder sb = new StringBuilder();
        String wcol = weight > hinge.weightLimit * 0.9f ? "[orange]" : "[lime]";
        sb.append("Weight: ").append(wcol).append(Strings.autoFixed(weight, 1)).append(" / ")
            .append(Strings.autoFixed(hinge.weightLimit, 0)).append("[]\n");
        sb.append("Health: ").append(Strings.autoFixed(health, 0)).append('\n');
        sb.append("Pivot: ").append(pivot == cornerPivot ? "cell corner (centres 2-wide)" : "cell centre (centres 1- and 3-wide)").append('\n');
        sb.append("Traverse: ").append(Strings.autoFixed(rotateSpeed * 60f, 0)).append(" deg/s\n");
        int ammo = ammo();
        sb.append("Ammo: ").append(ammo == CannonCaliber.he ? "[orange]HE[]" : ammo == CannonCaliber.ap ? "[sky]AP[]" : "standard").append('\n');

        IntIntMap counted = new IntIntMap();
        for(Line line : lines) counted.increment(line.group());
        for(Line line : lines){
            int count = counted.get(line.group(), 0);
            if(count == 0) continue;
            counted.put(line.group(), 0);

            sb.append(count).append("x ").append(CannonCaliber.get(line.caliber).title.toLowerCase())
                .append(", ").append(line.length).append(" long: [scarlet]")
                .append(Strings.autoFixed(damage(line), 0)).append("[] dmg, ")
                .append(Strings.autoFixed(60f / reload(line), 2)).append("/s, +-")
                .append(Strings.autoFixed(inaccuracy(line), 1)).append(" deg, ")
                .append(Strings.autoFixed(range(line) / 8f, 0)).append(" tiles\n");
        }

        if(lines.isEmpty()) sb.append("[scarlet]! Nothing can fire: put barrels in front of a breech[]\n");
        if(deadBarrels > 0) sb.append("[orange]! ").append(deadBarrels).append(" barrel(s) have no breech behind them[]\n");
        if(dryBreeches > 0) sb.append("[orange]! ").append(dryBreeches).append(" breech(es) have no barrel[]\n");
        if(lines.size > 1) sb.append("[lightgray]").append(lines.size).append(" lines of fire[]\n");
        return sb.toString();
    }
}
