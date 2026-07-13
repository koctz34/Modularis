package modularis.type.units;

import arc.struct.*;
import arc.util.*;

import modularis.content.*;
import modularis.type.units.modules.*;

public class ModularDesign{
    public final Seq<PlacedModule> modules = new Seq<>();

    public boolean isEmpty(){
        return modules.isEmpty();
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
            if(m.type.slot == type) n += m.type.slotCost;
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

            boolean fits = used + o.type.slotCost <= cap;
            if(fits) used += o.type.slotCost;
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
        return out;
    }

    public void set(ModularDesign other){
        modules.clear();
        for(PlacedModule m : other.modules) modules.add(m.copy());
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
        for(PlacedModule m : modules) v += m.type.weight;
        return v;
    }

    public float totalHealth(){
        float v = 0f;
        for(PlacedModule m : modules) v += m.type.health;
        return v;
    }

    /** Net power = production - use. Negative means the machine is under-powered. */
    public float powerBalance(){
        float v = 0f;
        for(PlacedModule m : modules) v += m.type.powerProduction - m.type.powerUse;
        return v;
    }

    public int count(ModuleCategory category){
        int c = 0;
        for(PlacedModule m : modules) if(m.type.category == category) c++;
        return c;
    }

    /** Total move speed contributed by all wheels. */
    public float wheelSpeed(){
        float v = 0f;
        for(PlacedModule m : modules){
            if(m.type instanceof ModulWheel w) v += w.moveSpeed;
        }
        return v;
    }

    public float wheelRotateSpeed(){
        float v = 0f;
        for(PlacedModule m : modules){
            if(m.type instanceof ModulWheel w) v += w.rotateSpeed;
        }
        return v;
    }

    // ---- serialization ----

    /** Compact string form: {@code name:x:y;name:x:y;...} */
    public String serialize(){
        StringBuilder sb = new StringBuilder();
        for(PlacedModule m : modules){
            sb.append(m.type.name).append(':').append(m.x).append(':').append(m.y).append(';');
        }
        return sb.toString();
    }

    public static ModularDesign read(String data){
        ModularDesign out = new ModularDesign();
        if(data == null || data.isEmpty()) return out;
        for(String part : data.split(";")){
            if(part.isEmpty()) continue;
            String[] f = part.split(":");
            if(f.length != 3) continue;
            ModuleType type = MdlModules.byName(f[0]);
            if(type == null) continue;
            try{
                out.modules.add(new PlacedModule(type, Integer.parseInt(f[1]), Integer.parseInt(f[2])));
            }catch(NumberFormatException ignored){}
        }
        return out;
    }
}
