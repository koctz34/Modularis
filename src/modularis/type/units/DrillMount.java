package modularis.type.units;

import modularis.type.units.modules.*;

/** Live grinding progress for one drill module on a modular unit. */
public class DrillMount{
    public final PlacedModule placed;
    public final ModulDrill type;

    /** Grinding progress towards the next item. */
    public float progress;

    public DrillMount(PlacedModule placed, ModulDrill type){
        this.placed = placed;
        this.type = type;
    }
}
