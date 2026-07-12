package modularis.type.units;

import modularis.type.units.modules.*;

/** Live pulse-charge state for one mender (ability) module on a modular unit. */
public class MenderMount{
    public final PlacedModule placed;
    public final ModulMender type;

    public float charge;

    public MenderMount(PlacedModule placed, ModulMender type){
        this.placed = placed;
        this.type = type;
    }
}
