package modularis.type.units;

import modularis.type.units.modules.*;

public class PlacedModule{
    public ModuleType type;
    public int x, y;

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
        return new PlacedModule(type, x, y);
    }
}
