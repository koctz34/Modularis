package modularis.type.units;

import arc.graphics.*;
import arc.util.*;

import modularis.type.units.modules.*;

public class PlacedModule{
    public ModuleType type;
    public int x, y;
    public @Nullable Color color;
    public @Nullable ModularDesign cannon;

    public PlacedModule(ModuleType type, int x, int y){
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public boolean covers(int cx, int cy){
        return cx >= x && cx < x + type.w && cy >= y && cy < y + type.h;
    }

    public boolean overlaps(int ox, int oy, int ow, int oh){
        return x < ox + ow && x + type.w > ox && y < oy + oh && y + type.h > oy;
    }

    public PlacedModule copy(){
        PlacedModule out = new PlacedModule(type, x, y);
        if(color != null) out.color = color.cpy();
        if(cannon != null) out.cannon = cannon.copy();
        return out;
    }

    public float weight(){
        return type.weight + (cannon == null ? 0f : cannon.totalWeight());
    }

    public float health(){
        return type.health + (cannon == null ? 0f : cannon.totalHealth());
    }
}
