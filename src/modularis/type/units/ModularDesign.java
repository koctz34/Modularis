package modularis.type.units;

import arc.graphics.*;
import arc.struct.*;
import arc.util.*;

import modularis.content.*;
import modularis.type.units.modules.*;

public class ModularDesign{
    public final Seq<PlacedModule> modules = new Seq<>();

    public final Color color = Color.white.cpy();
    /** Cannon designs only: where the pivot sits, see {@link CannonSpec#cellPivot}. */
    public float pivot = CannonSpec.cellPivot;

    public boolean isEmpty(){
        return modules.isEmpty();
    }

    public Color colorOf(PlacedModule m){
        return m.color != null ? m.color : color;
    }

    public @Nullable PlacedModule get(int cx, int cy){
        for(PlacedModule m : modules){
            if(m.covers(cx, cy)) return m;
        }
        return null;
    }

    public boolean canPlace(ModuleType type, int x, int y){
        //count limit and free slots
        if(!canAdd(type)) return false;
        //no overlap with existing modules
        for(PlacedModule m : modules){
            if(m.overlaps(x, y, type.w, type.h)) return false;
        }
        return true;
    }

    /** True if another module of this type may be added at all (count limit + free slots). */
    public boolean canAdd(ModuleType type){
        if(atLimit(type)) return false;
        if(type.slot != SlotType.none && type.slotCost > slotsFree(type.slot)) return false;
        return true;
    }

    public boolean atLimit(ModuleType type){
        return type.limit >= 0 && count(type) >= type.limit;
    }

    // ---- slots: command cores provide them, weapons/engines/abilities consume them ----

    /** Total slots of this pool granted by the machine's command core(s). */
    public int slotsProvided(SlotType type){
        if(type == SlotType.none) return 0;
        int n = 0;
        for(PlacedModule m : modules){
            if(m.type instanceof ModulRoot r) n += r.slotsProvided(type);
        }
        return n;
    }

    /** Total slots of this pool consumed by the modules currently placed. */
    public int slotsUsed(SlotType type){
        if(type == SlotType.none) return 0;
        int n = 0;
        for(PlacedModule m : modules){
            if(m.type.slot == type) n += m.slotCost();
        }
        return n;
    }

    public int slotsFree(SlotType type){
        return slotsProvided(type) - slotsUsed(type);
    }

    /**
     * True if this module actually fits in the slots the command core(s) provide.
     *
     * Slots aren't just a build-time check: tear the core off a finished machine (or delete
     * it in the editor) and the modules it was powering must stop working. Modules are fitted
     * greedily in placement order - the ones that no longer fit go inert. They still weigh
     * what they weigh and still take hits; they just do nothing.
     *
     * Computed from the design alone, so the world, the editor, saves and clients all agree.
     */
    public boolean isActive(PlacedModule m){
        SlotType type = m.type.slot;
        if(type == SlotType.none) return true;

        int cap = slotsProvided(type);
        int used = 0;
        for(PlacedModule o : modules){
            if(o.type.slot != type) continue;

            boolean fits = used + o.slotCost() <= cap;
            if(fits) used += o.slotCost();
            if(o == m) return fits;
        }
        return false;
    }

    /** True if more slots are used than the core(s) provide (e.g. the core was removed). */
    public boolean slotsOverused(){
        for(SlotType t : SlotType.values()){
            if(t != SlotType.none && slotsFree(t) < 0) return true;
        }
        return false;
    }

    public int count(ModuleType type){
        int c = 0;
        for(PlacedModule m : modules) if(m.type == type) c++;
        return c;
    }

    public boolean place(ModuleType type, int x, int y){
        if(!canPlace(type, x, y)) return false;
        modules.add(new PlacedModule(type, x, y));
        return true;
    }

    public boolean removeAt(int cx, int cy){
        PlacedModule m = get(cx, cy);
        if(m != null){
            modules.remove(m);
            return true;
        }
        return false;
    }

    public void clear(){
        modules.clear();
    }

    public ModularDesign copy(){
        ModularDesign out = new ModularDesign();
        for(PlacedModule m : modules) out.modules.add(m.copy());
        out.color.set(color);
        out.pivot = pivot;
        return out;
    }

    public void set(ModularDesign other){
        modules.clear();
        for(PlacedModule m : other.modules) modules.add(m.copy());
        color.set(other.color);
        pivot = other.pivot;
    }

    public static ModularDesign legal(ModularDesign raw, int[] dropped){
        ModularDesign out = new ModularDesign();
        out.color.set(raw.color);
        for(PlacedModule m : raw.modules){
            if(MdlModules.cannonParts.contains(m.type) || out.atLimit(m.type) || overlapsAny(out, m)){
                dropped[0]++;
                continue;
            }
            PlacedModule placed = new PlacedModule(m.type, m.x, m.y);
            placed.color = m.color == null ? null : m.color.cpy();
            if(m.type instanceof ModulHinge hinge && m.cannon != null){
                placed.cannon = new ModularDesign();
                placed.cannon.color.set(m.cannon.color);
                placed.cannon.pivot = m.cannon.pivot;
                for(PlacedModule p : m.cannon.modules){
                    if(!MdlModules.cannonParts.contains(p.type) || p.type instanceof CannonPart c && c.unstable && !WorldTally.tuned()
                        || !CannonSpec.canPlace(placed.cannon, hinge, p.type, p.x, p.y)){
                        dropped[0]++;
                        continue;
                    }
                    PlacedModule part = new PlacedModule(p.type, p.x, p.y);
                    part.color = p.color == null ? null : p.color.cpy();
                    placed.cannon.modules.add(part);
                }
            }
            out.modules.add(placed);
        }
        return out;
    }

    private static boolean overlapsAny(ModularDesign design, PlacedModule m){
        for(PlacedModule o : design.modules){
            if(o.overlaps(m.x, m.y, m.type.w, m.type.h)) return true;
        }
        return false;
    }

    public boolean isExposed(PlacedModule m){
        for(int cx = m.x; cx < m.x + m.type.w; cx++){
            for(int cy = m.y; cy < m.y + m.type.h; cy++){
                if(get(cx - 1, cy) == null || get(cx + 1, cy) == null
                    || get(cx, cy - 1) == null || get(cx, cy + 1) == null){
                    return true;
                }
            }
        }
        return false;
    }

    // ---- bounds (in cells) ----

    public int minX(){
        int v = Integer.MAX_VALUE;
        for(PlacedModule m : modules) v = Math.min(v, m.x);
        return modules.isEmpty() ? 0 : v;
    }

    public int minY(){
        int v = Integer.MAX_VALUE;
        for(PlacedModule m : modules) v = Math.min(v, m.y);
        return modules.isEmpty() ? 0 : v;
    }

    public int maxX(){
        int v = Integer.MIN_VALUE;
        for(PlacedModule m : modules) v = Math.max(v, m.x + m.type.w);
        return modules.isEmpty() ? 0 : v;
    }

    public int maxY(){
        int v = Integer.MIN_VALUE;
        for(PlacedModule m : modules) v = Math.max(v, m.y + m.type.h);
        return modules.isEmpty() ? 0 : v;
    }

    public float centerX(){
        return (minX() + maxX()) / 2f;
    }

    public float centerY(){
        return (minY() + maxY()) / 2f;
    }

    public int widthCells(){
        return maxX() - minX();
    }

    public int heightCells(){
        return maxY() - minY();
    }

    // ---- aggregate stats ----

    public float totalWeight(){
        float v = 0f;
        for(PlacedModule m : modules) v += m.weight();
        return v;
    }

    public float totalHealth(){
        float v = 0f;
        for(PlacedModule m : modules) v += m.health();
        return v;
    }

    /** Net power = production - use. Negative means the machine is under-powered. */
    public float powerBalance(){
        float v = 0f;
        for(PlacedModule m : modules) v += m.type.powerProduction - m.powerUse();
        return v;
    }

    public int count(ModuleCategory category){
        int c = 0;
        for(PlacedModule m : modules) if(m.type.category == category) c++;
        return c;
    }

    /** Total move speed contributed by all drive parts, whatever they propel with. */
    public float wheelSpeed(){
        float v = 0f;
        for(PlacedModule m : modules){
            if(m.type instanceof ModulPropulsor p) v += p.moveSpeed;
        }
        return v;
    }

    public float wheelRotateSpeed(){
        float v = 0f;
        for(PlacedModule m : modules){
            if(m.type instanceof ModulPropulsor p) v += p.rotateSpeed;
        }
        return v;
    }

    // ---- serialization ----
    public String serialize(){
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(color.toString()).append(';');
        for(PlacedModule m : modules){
            sb.append(m.type.name).append(':').append(m.x).append(':').append(m.y);
            if(m.color != null) sb.append(':').append(m.color);
            if(m.cannon != null){
                sb.append('@');
                if(!m.cannon.color.equals(Color.white)) sb.append('#').append(m.cannon.color).append('/');
                if(m.cannon.pivot == CannonSpec.cornerPivot) sb.append("%/");
                for(PlacedModule p : m.cannon.modules){
                    sb.append(p.type.name).append(',').append(p.x).append(',').append(p.y);
                    if(p.color != null) sb.append(',').append(p.color);
                    sb.append('/');
                }
            }
            sb.append(';');
        }
        return sb.toString();
    }

    public static ModularDesign read(String data){
        ModularDesign out = new ModularDesign();
        if(data == null || data.isEmpty()) return out;
        for(String part : data.split(";")){
            if(part.isEmpty()) continue;

            //paint job
            if(part.charAt(0) == '#'){
                try{
                    out.color.set(Color.valueOf(part.substring(1)));
                }catch(Exception ignored){}
                continue;
            }

            String cannon = null;
            int at = part.indexOf('@');
            if(at >= 0){
                cannon = part.substring(at + 1);
                part = part.substring(0, at);
            }

            String[] f = part.split(":");
            if(f.length != 3 && f.length != 4) continue;
            ModuleType type = MdlModules.byName(f[0]);
            if(type == null) continue;
            try{
                PlacedModule placed = new PlacedModule(type, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                if(f.length == 4) placed.color = Color.valueOf(f[3]);
                if(cannon != null) placed.cannon = readCannon(cannon);
                out.modules.add(placed);
            }catch(RuntimeException ignored){}
        }
        return out;
    }

    private static ModularDesign readCannon(String data){
        ModularDesign out = new ModularDesign();
        for(String part : data.split("/")){
            if(part.isEmpty()) continue;
            try{
                if(part.charAt(0) == '#'){
                    out.color.set(Color.valueOf(part.substring(1)));
                    continue;
                }
                if(part.charAt(0) == '%'){
                    out.pivot = CannonSpec.cornerPivot;
                    continue;
                }

                String[] f = part.split(",");
                if(f.length != 3 && f.length != 4) continue;
                ModuleType type = MdlModules.byName(f[0]);
                if(type == null) continue;

                PlacedModule placed = new PlacedModule(type, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                if(f.length == 4) placed.color = Color.valueOf(f[3]);
                out.modules.add(placed);
            }catch(RuntimeException ignored){}
        }
        return out;
    }
}
